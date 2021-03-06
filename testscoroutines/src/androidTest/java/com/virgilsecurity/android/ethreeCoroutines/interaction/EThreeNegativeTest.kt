/*
 * Copyright (c) 2015-2018, Virgil Security, Inc.
 *
 * Lead Maintainer: Virgil Security Inc. <support@virgilsecurity.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     (1) Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *     (3) Neither the name of virgil nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.virgilsecurity.android.ethreeCoroutines.interaction

import com.virgilsecurity.android.ethreecoroutines.interaction.EThree
import com.virgilsecurity.android.common.exceptions.NotBootstrappedException
import com.virgilsecurity.android.common.exceptions.PublicKeyDuplicateException
import com.virgilsecurity.android.common.exceptions.PublicKeyNotFoundException
import com.virgilsecurity.android.ethreeCoroutines.extension.awaitResult
import com.virgilsecurity.android.ethreeCoroutines.model.onError
import com.virgilsecurity.android.ethreeCoroutines.utils.TestConfig
import com.virgilsecurity.sdk.common.TimeSpan
import com.virgilsecurity.sdk.crypto.VirgilAccessTokenSigner
import com.virgilsecurity.sdk.jwt.JwtGenerator
import com.virgilsecurity.sdk.storage.DefaultKeyStorage
import com.virgilsecurity.sdk.storage.KeyStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by:
 * Danylo Oliinyk
 * on
 * 10/24/18
 * at Virgil Security
 */
class EThreeNegativeTest {

    private lateinit var jwtGenerator: JwtGenerator
    private lateinit var keyStorage: KeyStorage
    private lateinit var eThree: EThree
    private val identity = UUID.randomUUID().toString()
    private val password = UUID.randomUUID().toString()


    @Before
    fun setup() {
        jwtGenerator = JwtGenerator(
            TestConfig.appId,
            TestConfig.apiKey,
            TestConfig.apiPublicKeyId,
            TimeSpan.fromTime(600, TimeUnit.SECONDS),
            VirgilAccessTokenSigner(TestConfig.virgilCrypto)
        )

        keyStorage = DefaultKeyStorage(TestConfig.DIRECTORY_PATH, TestConfig.KEYSTORE_NAME)
        eThree = initEThree(identity)
    }

    private fun initEThree(identity: String): EThree {
        var eThree: EThree? = null

        runBlocking {
            eThree = EThree.initialize(
                TestConfig.context) {
                jwtGenerator.generateToken(identity).stringRepresentation()
            }.await()
        }

        return eThree!!
    }

    private fun bootstrapEThree(eThree: EThree): EThree {
        runBlocking {
            eThree.bootstrap().await()
        }

        return eThree
    }

    @Test(expected = NotBootstrappedException::class)
    fun cleanup_fail_without_bootstrap() {
        eThree.cleanup()
    }

    @Test
    fun backup_fail_without_bootstrap() {
        var failed = false
        runBlocking {
            eThree.backupPrivateKey(password).awaitResult().onError {
                if (it is NotBootstrappedException)
                    failed = true
            }
        }
        assertTrue(failed)
    }

    @Test
    fun reset_key_fail_without_bootstrap() {
        var failed = false
        runBlocking {
            eThree.resetPrivateKeyBackup(password).awaitResult().onError {
                if (it is NotBootstrappedException)
                    failed = true
            }
        }
        assertTrue(failed)
    }

    @Test
    fun change_pass_fail_without_bootstrap() {
        var failed = false
        runBlocking {
            eThree.changePassword(password, password + password).awaitResult().onError {
                if (it is NotBootstrappedException)
                    failed = true
            }
        }
        assertTrue(failed)
    }

    @Test(expected = NotBootstrappedException::class)
    fun encrypt_text_fail_without_bootstrap() {
        eThree.encrypt("")
    }

    @Test(expected = NotBootstrappedException::class)
    fun encrypt_data_fail_without_bootstrap() {
        eThree.encrypt(ByteArray(0))
    }

    @Test(expected = NotBootstrappedException::class)
    fun decrypt_text_fail_without_bootstrap() {
        eThree.decrypt("")
    }

    @Test(expected = NotBootstrappedException::class)
    fun decrypt_data_fail_without_bootstrap() {
        eThree.decrypt(ByteArray(0))
    }

    @Test
    fun lookup_fail_without_bootstrap() {
        var failed = false
        runBlocking {
            eThree.lookupPublicKeys(listOf("")).awaitResult().onError {
                failed = true
            }
        }
        assertTrue(failed)
    }

    @Test
    fun lookup_fail_wrong_identity() {
        bootstrapEThree(eThree)

        var failed = false
        runBlocking {
            eThree.lookupPublicKeys(listOf(identity,
                                           WRONG_IDENTITY)).awaitResult().onError {
                if (it is PublicKeyNotFoundException && it.identity == WRONG_IDENTITY)
                    failed = true
            }
        }
        assertTrue(failed)
    }

    @Test
    fun init_ethree_with_empty_token() {
        var failed = false
        runBlocking {
            EThree.initialize(TestConfig.context) { "" }.awaitResult().onError {
                failed = true
            }
        }
        assertTrue(failed)
    }

    @Test
    fun lookup_with_duplicate_identities() {
        var failed = false
        bootstrapEThree(eThree)

        runBlocking {
            eThree.lookupPublicKeys(listOf(identity, identity, identity,
                                           WRONG_IDENTITY,
                                           WRONG_IDENTITY,
                                           WRONG_IDENTITY + identity)).awaitResult().onError {
                if (it is PublicKeyDuplicateException)
                    failed = true
            }
        }
        assertTrue(failed)
    }

    @Test
    fun change_pass_with_same_new() {
        var failed = false
        bootstrapEThree(eThree)
        runBlocking {
            eThree.changePassword(password, password).awaitResult().onError {
                if (it is IllegalArgumentException)
                    failed = true
            }
        }
        assertTrue(failed)
    }

    companion object {
        const val WRONG_IDENTITY = "WRONG_IDENTITY"
    }
}