package com.cedarsoftware.servlet

import org.junit.Test

/**
 * Created by jderegnaucourt on 2015/03/07.
 */
class TestEnvelope
{
    @Test
    void testEnvelope()
    {
        Envelope envelope = new Envelope('["a", "b", "c"]', true)
        assert envelope.data == '["a", "b", "c"]'
        assert envelope.status

        envelope = new Envelope(10L, false)
        assert envelope.data == 10L
        assert !envelope.status
    }
}
