/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.codingjam.github.ui.repo

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.support.v4.app.Fragment
import com.nhaarman.mockito_kotlin.mock
import it.codingjam.github.NavigationController
import it.codingjam.github.core.GithubInteractor
import it.codingjam.github.core.RepoId
import it.codingjam.github.test.willReturn
import it.codingjam.github.test.willThrow
import it.codingjam.github.testdata.TestData
import it.codingjam.github.testdata.shouldContain
import it.codingjam.github.util.TestCoroutines
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RepoViewModelTest {

    @get:Rule var instantExecutorRule = InstantTaskExecutorRule()

    val interactor: GithubInteractor = mock()

    val navigationController: NavigationController = mock()

    val fragment: Fragment = mock()

    val repoViewModel by lazy { RepoViewModel(navigationController, interactor, TestCoroutines(), RepoId("a", "b")) }

    val states = mutableListOf<RepoViewState>()

    @Before fun setUp() {
        repoViewModel.state.observeForever { states.add(it) }
        repoViewModel.uiActions.observeForever { it(fragment) }
    }

    @Test fun fetchData() = runBlocking {
        interactor.loadRepo("a", "b") willReturn TestData.REPO_DETAIL

        repoViewModel.reload()

        states.map { it } shouldContain {
            loading().loading().success()
        }
    }

    @Test fun errorFetchingData() = runBlocking {
        interactor.loadRepo("a", "b") willThrow RuntimeException()

        repoViewModel.reload()

        states.map { it } shouldContain {
            loading().loading().error()
        }
    }

    @Test fun retry() = runBlocking {
        interactor.loadRepo("a", "b")
                .willThrow(RuntimeException())
                .willReturn(TestData.REPO_DETAIL)

        repoViewModel.reload()

        repoViewModel.reload()

        states.map { it } shouldContain {
            loading().loading().error().loading().success()
        }
    }
}