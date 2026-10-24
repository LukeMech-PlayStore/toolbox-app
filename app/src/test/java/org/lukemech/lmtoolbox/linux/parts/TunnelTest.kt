package org.lukemech.lmtoolbox.linux.parts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunnelTest {
    private val token = "eyJhIjoiNmQ4ZTFhYjIzNCIsInQiOiI5ZjNkMmM0NS1hYmNkIiwicyI6Ik1qRTVNalExIn0=" // gitleaks:allow (a made-up token, as the dashboard shows one)

    @Test
    fun tokenAsPasted() {
        assertEquals(token, Tunnel.tokenIn(token))
        assertEquals(token, Tunnel.tokenIn("  $token\n"))
        // The dashboard's install commands
        assertEquals(token, Tunnel.tokenIn("sudo cloudflared service install $token"))
        assertEquals(token, Tunnel.tokenIn("cloudflared tunnel run --token $token"))
        assertEquals(token, Tunnel.tokenIn("docker run cloudflare/cloudflared:latest tunnel --no-autoupdate run --token \"$token\""))
    }

    @Test
    fun noToken() {
        assertNull(Tunnel.tokenIn(""))
        assertNull(Tunnel.tokenIn("cloudflared service install"))
        assertNull(Tunnel.tokenIn("not a token at all, just words"))
    }
}
