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

package com.virgilsecurity.android.ethree.kotlin.interaction

import com.virgilsecurity.android.common.exceptions.NotBootstrappedException
import com.virgilsecurity.sdk.cards.CardManager
import com.virgilsecurity.sdk.cards.model.RawSignedModel
import com.virgilsecurity.sdk.cards.validation.VirgilCardVerifier
import com.virgilsecurity.sdk.client.CardClient
import com.virgilsecurity.sdk.common.TimeSpan
import com.virgilsecurity.sdk.crypto.*
import com.virgilsecurity.sdk.exception.EmptyArgumentException
import com.virgilsecurity.sdk.jwt.JwtGenerator
import com.virgilsecurity.sdk.jwt.accessProviders.GeneratorJwtProvider
import com.virgilsecurity.sdk.storage.DefaultKeyStorage
import com.virgilsecurity.sdk.storage.JsonKeyEntry
import com.virgilsecurity.sdk.storage.KeyStorage
import com.virgilsecurity.sdk.utils.Tuple
import com.virgilsecurity.android.ethree.utils.TestUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.virgilsecurity.android.ethree.utils.TestConfig
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by:
 * Danylo Oliinyk
 * on
 * 10/9/18
 * at Virgil Security
 */
class EThreeEncryptionTest {

    private val identity = UUID.randomUUID().toString()

    private lateinit var eThree: EThree
    private lateinit var jwtGenerator: JwtGenerator
    private lateinit var keyStorage: KeyStorage

    @Before
    fun setup() {
        jwtGenerator = JwtGenerator(
            TestConfig.appId,
            TestConfig.apiKey,
            TestConfig.apiPublicKeyId,
            TimeSpan.fromTime(600, TimeUnit.SECONDS),
            VirgilAccessTokenSigner(TestConfig.virgilCrypto)
        )

        eThree = initAndBootstrapEThree(identity)
        keyStorage = DefaultKeyStorage(TestConfig.DIRECTORY_PATH, TestConfig.KEYSTORE_NAME)
    }

    private fun initAndBootstrapEThree(identity: String): EThree {
        val eThree = initEThree(identity)
        bootstrapEThree(eThree)
        return eThree
    }

    private fun initEThree(identity: String): EThree {
        var eThree: EThree? = null
        val waiter = CountDownLatch(1)

        EThree.initialize(TestConfig.context, object : EThree.OnGetTokenCallback {
            override fun onGetToken(): String {
                return jwtGenerator.generateToken(identity).stringRepresentation()
            }
        }, object : EThree.OnResultListener<EThree> {
            override fun onSuccess(result: EThree) {
                eThree = result
                waiter.countDown()
            }

            override fun onError(throwable: Throwable) {
                fail(throwable.message)
            }

        })

        waiter.await(TestUtils.THROTTLE_TIMEOUT, TimeUnit.SECONDS)

        return eThree!!
    }

    private fun bootstrapEThree(eThree: EThree): EThree {
        val waiter = CountDownLatch(1)

        eThree.bootstrap(object : EThree.OnCompleteListener {

            override fun onSuccess() {
                // Good, go on
                waiter.countDown()
            }

            override fun onError(throwable: Throwable) {
                fail(throwable.message)
            }
        })

        waiter.await(TestUtils.THROTTLE_TIMEOUT, TimeUnit.SECONDS)

        return eThree
    }

    private fun initCardManager(identity: String): CardManager {
        val cardCrypto = VirgilCardCrypto()
        return CardManager(
            cardCrypto,
            GeneratorJwtProvider(jwtGenerator, identity),
            VirgilCardVerifier(cardCrypto, false, false),
            CardClient(TestConfig.virgilBaseUrl + TestConfig.VIRGIL_CARDS_SERVICE_PATH)
        )
    }

    private fun generateRawCard(identity: String, cardManager: CardManager): Tuple<VirgilKeyPair, RawSignedModel> {
        return VirgilCrypto().generateKeys().let {
            Tuple(it, cardManager.generateRawCard(it.privateKey, it.publicKey, identity))
        }
    }

    @Test
    fun lookup_one_user() {
        val identityOne = UUID.randomUUID().toString()
        val cardManagerOne = initCardManager(identityOne)
        val publishedCardOne = cardManagerOne.publishCard(generateRawCard(identityOne,
                                                                          cardManagerOne).right)

        eThree.lookupPublicKeys(listOf(identityOne),
                                object : EThree.OnResultListener<Map<String, PublicKey>> {
                                    override fun onSuccess(result: Map<String, PublicKey>) {
                                        assertTrue(result.isNotEmpty() && result.size == 1)
                                        assertEquals(publishedCardOne.publicKey, result[identityOne])
                                    }

                                    override fun onError(throwable: Throwable) {
                                        fail(throwable.message)
                                    }
                                })
    }

    // STE-Encrypt-1
    @Test
    fun lookup_multiply_users() {
        var foundCards = false

        // Card one
        val identityOne = UUID.randomUUID().toString()
        val cardManagerOne = initCardManager(identityOne)
        val publishedCardOne = cardManagerOne.publishCard(generateRawCard(identityOne,
                                                                          cardManagerOne).right)
        // Card two
        val identityTwo = UUID.randomUUID().toString()
        val cardManagerTwo = initCardManager(identityTwo)
        val publishedCardTwo = cardManagerTwo.publishCard(generateRawCard(identityTwo,
                                                                          cardManagerTwo).right)
        // Card three
        val identityThree = UUID.randomUUID().toString()
        val cardManagerThree = initCardManager(identityThree)
        val publishedCardThree = cardManagerThree.publishCard(generateRawCard(identityThree,
                                                                              cardManagerThree).right)

        eThree.lookupPublicKeys(listOf(identityOne, identityTwo, identityThree),
                                object : EThree.OnResultListener<Map<String, PublicKey>> {

                                    override fun onSuccess(result: Map<String, PublicKey>) {
                                        assertTrue(result.isNotEmpty() && result.size == 3)
                                            if (result[identityOne] == publishedCardOne.publicKey
                                                 && result[identityTwo] == publishedCardTwo.publicKey
                                                 && result[identityThree] == publishedCardThree.publicKey) {
                                                foundCards= true
                                            }

                                        assertTrue(foundCards)
                                    }

                                    override fun onError(throwable: Throwable) {
                                        fail(throwable.message)
                                    }
                                })
    }

    //STE-Encrypt-2
    @Test
    fun lookup_zero_users() {
        eThree.lookupPublicKeys(listOf(), object : EThree.OnResultListener<Map<String, PublicKey>> {
            override fun onSuccess(result: Map<String, PublicKey>) {
                fail("Illegal State")
            }

            override fun onError(throwable: Throwable) {
                assertTrue(throwable is EmptyArgumentException)
            }
        })
    }

    @Test
    fun encrypt_adding_owner_public_key() {
        val identityTwo = UUID.randomUUID().toString()
        initAndBootstrapEThree(identityTwo)

        val eThreeKeys = mutableListOf<PublicKey>()

        val waiter = CountDownLatch(1)
        eThree.lookupPublicKeys(listOf(identity, identityTwo),
                                object : EThree.OnResultListener<Map<String, PublicKey>> {
                                    override fun onSuccess(result: Map<String, PublicKey>) {
                                        eThreeKeys.addAll(result.values.toList())
                                        waiter.countDown()
                                    }

                                    override fun onError(throwable: Throwable) {
                                        fail(throwable.message)
                                    }
                                })
        waiter.await(TestUtils.THROTTLE_TIMEOUT, TimeUnit.SECONDS)
        assertTrue(eThreeKeys.size == 2)

        var failedEncrypt = false
        try {
            eThree.encrypt(RAW_TEXT, eThreeKeys)
        } catch (e: IllegalArgumentException) {
            failedEncrypt = true
        }
        assertTrue(failedEncrypt)
    }

    // STE-Encrypt-3
    @Test
    fun encrypt_decrypt() {
        val identityTwo = UUID.randomUUID().toString()
        val eThreeTwo = initAndBootstrapEThree(identityTwo)

        val eThreeKeys = mutableListOf<PublicKey>()

        val waiter = CountDownLatch(1)
        eThree.lookupPublicKeys(listOf(identity, identityTwo),
                                object : EThree.OnResultListener<Map<String, PublicKey>> {
                                    override fun onSuccess(result: Map<String, PublicKey>) {
                                        eThreeKeys.addAll(result.values.toList())
                                        waiter.countDown()
                                    }

                                    override fun onError(throwable: Throwable) {
                                        fail(throwable.message)
                                    }
                                })
        waiter.await(TestUtils.THROTTLE_TIMEOUT, TimeUnit.SECONDS)

        assertTrue(eThreeKeys.size == 2)
        val encryptedForOne = eThree.encrypt(RAW_TEXT, listOf(eThreeKeys[1]))

        val wrongPublicKey = TestConfig.virgilCrypto.generateKeys().publicKey
        var failedWithWrongKey = false
        try {
            eThreeTwo.decrypt(encryptedForOne, wrongPublicKey)
        } catch (throwable: Throwable) {
            failedWithWrongKey = true
        }
        assertTrue(failedWithWrongKey)

        val decryptedByTwo = eThreeTwo.decrypt(encryptedForOne, eThreeKeys[0])

        assertEquals(RAW_TEXT, decryptedByTwo)
    }

    // STE-Encrypt-4
    @Test(expected = EmptyArgumentException::class)
    fun encrypt_for_zero_users() {
        eThree.encrypt(RAW_TEXT, listOf())
    }

    // STE-Encrypt-6
    @Test
    fun encrypt_decrypt_for_owner() {
        val encryptedText = eThree.encrypt(RAW_TEXT)
        val decryptedText = eThree.decrypt(encryptedText)

        assertEquals(RAW_TEXT, decryptedText)
    }

    // STE-Encrypt-7
    @Test
    fun encrypt_without_sign() {
        val keyPair = TestConfig.virgilCrypto.generateKeys()
        val encryptedWithoutSign = TestConfig.virgilCrypto.encrypt(RAW_TEXT.toByteArray(),
                                                                   keyPair.publicKey)

        var failedDecrypt = false
        try {
            eThree.decrypt(encryptedWithoutSign, keyPair.publicKey)
        } catch (e: Exception) {
            failedDecrypt = true
        }
        assertTrue(failedDecrypt)
    }

    // STE-Encrypt-8
    @Test
    fun init_without_local_key() {
        val identityTwo = UUID.randomUUID().toString()
        val eThreeTwo = initEThree(identityTwo)
        val anyKeypair = TestConfig.virgilCrypto.generateKeys()

        var failedEncrypt = false
        try {
            eThreeTwo.encrypt(RAW_TEXT, listOf(anyKeypair.publicKey))
        } catch (e: NotBootstrappedException) {
            failedEncrypt = true
        }
        assertTrue(failedEncrypt)

        var failedDecrypt = false
        try {
            eThreeTwo.decrypt(RAW_TEXT, anyKeypair.publicKey)
        } catch (e: NotBootstrappedException) {
            failedDecrypt = true
        }
        assertTrue(failedDecrypt)
    }

    // STE-Encrypt-9
    @Test
    fun init_without_local_key_and_create_after() {
        val identityTwo = UUID.randomUUID().toString()
        val eThreeTwo = initEThree(identityTwo)

        val anyKeypair = TestConfig.virgilCrypto.generateKeys()
        keyStorage.store(JsonKeyEntry(identityTwo, anyKeypair.privateKey.rawKey))

        val encrypted = eThreeTwo.encrypt(RAW_TEXT)
        val decrypted = eThreeTwo.decrypt(encrypted)

        assertEquals(RAW_TEXT, decrypted)
    }

    companion object {
        const val MULTIPLY_TIMES = 10
        const val RAW_TEXT = "This is the best text ever made by the whole humanity."
    }
}