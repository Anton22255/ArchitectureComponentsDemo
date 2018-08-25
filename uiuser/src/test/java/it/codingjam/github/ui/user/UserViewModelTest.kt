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

package it.codingjam.github.ui.user

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.support.v4.app.Fragment
import assertk.assert
import assertk.assertions.containsExactly
import com.nhaarman.mockito_kotlin.mock
import it.codingjam.github.NavigationController
import it.codingjam.github.core.GithubInteractor
import it.codingjam.github.core.RepoId
import it.codingjam.github.core.UserDetail
import it.codingjam.github.test.willReturn
import it.codingjam.github.test.willThrow
import it.codingjam.github.testdata.TestData.REPO_1
import it.codingjam.github.testdata.TestData.REPO_2
import it.codingjam.github.testdata.TestData.USER
import it.codingjam.github.util.TestCoroutines
import it.codingjam.github.vo.Lce
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class UserViewModelTest {
    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

    val githubInteractor: GithubInteractor = mock()
    val navigationController: NavigationController = mock()
    val fragment: Fragment = mock()
    val userViewModel by lazy { UserViewModel(githubInteractor, navigationController, TestCoroutines(), LOGIN) }

    val states = mutableListOf<UserViewState>()

    @Before fun setUp() {
        userViewModel.state.observeForever { states.add(it) }
        userViewModel.uiActions.observeForever { it(fragment) }
    }

    @Test fun load() {
        runBlocking {
            githubInteractor.loadUserDetail(LOGIN) willReturn UserDetail(USER, listOf(REPO_1, REPO_2))
        }

        userViewModel.load()

        assert(states)
                .containsExactly(
                        Lce.Loading,
                        Lce.Loading,
                        Lce.Success(UserDetail(USER, listOf(REPO_1, REPO_2)))
                )
    }

    @Test fun retry() {
        runBlocking {
            githubInteractor.loadUserDetail(LOGIN)
                    .willThrow(RuntimeException(ERROR))
                    .willReturn(UserDetail(USER, listOf(REPO_1, REPO_2)))
        }

        userViewModel.load()
        userViewModel.retry()

        assert(states)
                .containsExactly(
                        Lce.Loading,
                        Lce.Loading,
                        Lce.Error(ERROR),
                        Lce.Loading,
                        Lce.Success(UserDetail(USER, listOf(REPO_1, REPO_2)))
                )
    }

    @Test fun openRepoDetail() {
        userViewModel.openRepoDetail(REPO_ID)

        verify(navigationController).navigateToRepo(fragment, REPO_ID)
    }

    companion object {
        private val LOGIN = "login"
        private val ERROR = "error"
        private val REPO_ID = RepoId("owner", "name")
    }
}