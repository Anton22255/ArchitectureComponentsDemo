package it.codingjam.github.util


import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.support.annotation.MainThread
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.runBlocking
import java.util.*

interface Action<out T>

class StateAction<T>(private val f: T.() -> T) : Action<T> {
    operator fun invoke(t: T) = t.f()
}

interface Signal : Action<Nothing>

data class ErrorSignal(val error: Throwable?, val message: String) : Signal {
    constructor(t: Throwable) : this(t, t.message ?: "Error ${t.javaClass.name}")
}

data class NavigationSignal<P>(val destination: Any, val params: P) : Signal

class ViewStateStore<T : Any>(
        private val coroutines: Coroutines,
        initialState: T,
        private val liveData: MutableLiveData<T> = MutableLiveData()
) {

    init {
        liveData.value = initialState
    }

    private val delegate = MutableLiveData<List<Signal>>()

    private var list: MutableList<Signal> = ArrayList()

    fun observe(owner: LifecycleOwner, observer: (T) -> Unit) =
            liveData.observe(owner, Observer { observer(it!!) })

    fun observeSignals(owner: LifecycleOwner, executor: (Signal) -> Unit) =
            delegate.observe(owner, Observer { _ ->
                list.forEach { executor(it) }
                list = ArrayList()
            })

    @MainThread
    fun dispatchState(state: T) {
        liveData.value = state
    }

    fun dispatchSignal(action: Signal) {
        list.add(action)
        delegate.value = list
    }

    private fun dispatch(action: Action<T>) {
        if (action is StateAction<T>) {
            liveData.value = action(invoke())
        } else if (action is Signal) {
            list.add(action)
            delegate.value = list
        }
    }

    fun dispatchAction(f: suspend () -> Action<T>) {
        coroutines {
            val action = f()
            coroutines.onUi {
                dispatch(action)
            }
        }
    }

//    fun dispatchSignal(f: suspend () -> Signal) {
//        coroutines {
//            val signal = f()
//            coroutines.onUi {
//                dispatch(signal)
//            }
//        }
//    }

    fun dispatchActions(channel: ReceiveActionChannel<T>) {
        coroutines {
            channel.channel.consumeEach { action ->
                coroutines.onUi {
                    dispatch(action)
                }
            }
        }
    }

    operator fun invoke() = liveData.value!!

    fun cancel() = coroutines.cancel()
}

suspend fun <S> ProducerScope<Action<S>>.send(action: S.() -> S) = send(StateAction(action))

suspend inline fun <T : Any> ReceiveActionChannel<T>.states(initialState: T): List<Any> {
    return channel.fold(emptyList()) { states, action ->
        val element: Any = if (action is StateAction)
            action(states.lastOrNull() as T? ?: initialState)
        else
            action
        states + element
    }
}

suspend inline fun <reified S : Any> states(
        initialState: S,
        crossinline f: suspend (S) -> ReceiveActionChannel<S>
): List<S> =
        f(initialState)
                .states(initialState)
                .filterIsInstance<S>()

inline fun <reified S : Any> signals(
        initialState: S,
        crossinline f: suspend (S) -> ReceiveActionChannel<S>
): List<Signal> = runBlocking {
    f(initialState)
            .states(initialState)
            .filterIsInstance<Signal>()
}

class ReceiveActionChannel<T>(val channel: ReceiveChannel<Action<T>>) {
    fun <R> map(copy: R.(StateAction<T>) -> R) =
            ReceiveActionChannel(channel.map { action: Action<T> -> action.map(copy) })
}

suspend fun <T> ProducerScope<Action<T>>.sendAll(channel: ReceiveActionChannel<T>) {
    channel.channel.consumeEach { originalAction ->
        send(originalAction)
    }
}

fun <T> produceActions(f: suspend ProducerScope<Action<T>>.() -> Unit): ReceiveActionChannel<T> =
        ReceiveActionChannel(GlobalScope.produce(block = f))

fun <R, S> Action<R>.map(copy: S.(StateAction<R>) -> S): Action<S> {
    return if (this is Signal) {
        this
    } else {
        val stateAction = this as StateAction<R>
        StateAction { copy(stateAction) }
    }
}