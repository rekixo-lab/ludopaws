package com.rekixo.ludopaws.feature.online.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCharacterIdentityContractTest {
    @Test
    fun `purchased paws retain their canonical identity in remote requests`() {
        for (id in listOf("panda", "monkey", "horse", "elephant", "cock", "pig", "rabbit", "goat_lumi", "donkey")) {
            assertEquals(id, LudoPawsCharacterIdentityContract.resolveForRequest(id))
        }
    }

    @Test
    fun `legacy aliases and invalid identities remain safe`() {
        assertEquals("dog", LudoPawsCharacterIdentityContract.resolveForRequest("squirrel"))
        assertEquals("dog", LudoPawsCharacterIdentityContract.resolveForRequest("bad_not_registered"))
        assertEquals("cat", LudoPawsCharacterIdentityContract.resolveForRequest("cat"))
    }
}
