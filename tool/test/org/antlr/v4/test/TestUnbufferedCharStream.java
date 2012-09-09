/*
 [The "BSD license"]
  Copyright (c) 2011 Terence Parr
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
  3. The name of the author may not be used to endorse or promote products
     derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.test;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenFactory;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.UnbufferedCharStream;
import org.antlr.v4.tool.LexerGrammar;
import org.antlr.v4.tool.interp.LexerInterpreter;
import org.junit.Test;

import java.io.StringReader;

public class TestUnbufferedCharStream extends BaseTest {
	@Test public void testNoChar() throws Exception {
		CharStream input = new UnbufferedCharStream(
				new StringReader("")
		);
		assertEquals(CharStream.EOF, input.LA(1));
		input.consume();
		assertEquals(CharStream.EOF, input.LA(1));
		input.consume();
		assertEquals(CharStream.EOF, input.LA(1));
	}

	@Test public void test1Char() throws Exception {
		UnbufferedCharStream input = new UnbufferedCharStream(
				new StringReader("x")
		);
		assertEquals('x', input.LA(1));
		input.consume();
		assertEquals(CharStream.EOF, input.LA(1));
		assertEquals("\uFFFF", input.getBuffer()); // shouldn't include x
	}

	@Test public void test2Char() throws Exception {
		UnbufferedCharStream input = new UnbufferedCharStream(
				new StringReader("xy")
		);
		assertEquals('x', input.LA(1));
		input.consume();
		assertEquals('y', input.LA(1));
		assertEquals("y", input.getBuffer()); // shouldn't include x
		input.consume();
		assertEquals(CharStream.EOF, input.LA(1));
	}

    @Test public void test2CharAhead() throws Exception {
   		CharStream input = new UnbufferedCharStream(
   				new StringReader("xy")
   		);
   		assertEquals('x', input.LA(1));
   		assertEquals('y', input.LA(2));
   		assertEquals(CharStream.EOF, input.LA(3));
   	}

    @Test public void testBufferExpand() throws Exception {
		UnbufferedCharStream input = new UnbufferedCharStream(
   				new StringReader("01234"),
                2 // buff size 2
   		);
   		assertEquals('0', input.LA(1));
        assertEquals('1', input.LA(2));
        assertEquals('2', input.LA(3));
        assertEquals('3', input.LA(4));
        assertEquals('4', input.LA(5));
		assertEquals("01234", input.getBuffer());
   		assertEquals(CharStream.EOF, input.LA(6));
   	}

    @Test public void testBufferWrapSize1() throws Exception {
   		CharStream input = new UnbufferedCharStream(
   				new StringReader("01234"),
                1 // buff size 1
   		);
        assertEquals('0', input.LA(1));
        input.consume();
        assertEquals('1', input.LA(1));
        input.consume();
        assertEquals('2', input.LA(1));
        input.consume();
        assertEquals('3', input.LA(1));
        input.consume();
        assertEquals('4', input.LA(1));
        input.consume();
   		assertEquals(CharStream.EOF, input.LA(1));
   	}

    @Test public void testBufferWrapSize2() throws Exception {
   		CharStream input = new UnbufferedCharStream(
   				new StringReader("01234"),
                2 // buff size 2
   		);
        assertEquals('0', input.LA(1));
        input.consume();
        assertEquals('1', input.LA(1));
        input.consume();
        assertEquals('2', input.LA(1));
        input.consume();
        assertEquals('3', input.LA(1));
        input.consume();
        assertEquals('4', input.LA(1));
        input.consume();
   		assertEquals(CharStream.EOF, input.LA(1));
   	}

	@Test public void test1Mark() throws Exception {
		UnbufferedCharStream input = new UnbufferedCharStream(
			new StringReader("xyz")
		);
		int m = input.mark();
		assertEquals('x', input.LA(1));
		assertEquals('y', input.LA(2));
		assertEquals('z', input.LA(3));
		input.release(m);
		assertEquals(CharStream.EOF, input.LA(4));
		assertEquals("xyz\uFFFF", input.getBuffer());
	}

	@Test public void test1MarkWithConsumesInSequence() throws Exception {
		UnbufferedCharStream input = new UnbufferedCharStream(
			new StringReader("xyz")
		);
		int m = input.mark();
		input.consume(); // x, moves to y
		input.consume(); // y
		input.consume(); // z, moves to EOF
		assertEquals(CharStream.EOF, input.LA(1));
		input.release(m);
		assertEquals("xyz\uFFFF", input.getBuffer());
	}

    @Test public void test2Mark() throws Exception {
		UnbufferedCharStream input = new UnbufferedCharStream(
   				new StringReader("xyz"),
                100
   		);
   		assertEquals('x', input.LA(1));
        input.consume(); // reset buffer index (p) to 0
        int m1 = input.mark();
		assertEquals(0, m1); // first marker dropped at buffer index 0
   		assertEquals('y', input.LA(1));
        input.consume();
        int m2 = input.mark();
		assertEquals(1, m2); // 2nd marker dropped at buffer index 1
		assertEquals("yz", input.getBuffer());
   		assertEquals('z', input.LA(1)); // forces load
		assertEquals("yz", input.getBuffer());
        input.release(m2); // noop since not earliest in buf
        input.consume();
        input.release(m1);
   		assertEquals(CharStream.EOF, input.LA(1));
		assertEquals("yz\uFFFF", input.getBuffer());
   	}

    @Test public void testAFewTokens() throws Exception {
        LexerGrammar g = new LexerGrammar(
                "lexer grammar t;\n"+
				"ID : 'a'..'z'+;\n" +
				"INT : '0'..'9'+;\n" +
				"SEMI : ';';\n" +
				"ASSIGN : '=';\n" +
				"PLUS : '+';\n" +
				"MULT : '*';\n" +
				"WS : ' '+;\n");
        // Tokens: 012345678901234567
        // Input:  x = 3 * 0 + 2 * 0;
		UnbufferedCharStream input = new UnbufferedCharStream(
        	new StringReader("x = 302 * 91 + 20234234 * 0;")
        );
        LexerInterpreter lexEngine = new LexerInterpreter(g);
		// copy text into tokens from char stream
		lexEngine.setTokenFactory(new CommonTokenFactory(true));
        lexEngine.setInput(input);
		CommonTokenStream tokens = new CommonTokenStream(lexEngine);
        String result = tokens.LT(1).getText();
        String expecting = "x";
        assertEquals(expecting, result);
		tokens.fill();
		expecting =
			"[[@0,0:0='x',<1>,1:0], [@1,1:1=' ',<7>,1:1], [@2,2:2='=',<4>,1:2]," +
			" [@3,3:3=' ',<7>,1:3], [@4,4:6='302',<2>,1:4], [@5,7:7=' ',<7>,1:7]," +
			" [@6,8:8='*',<6>,1:8], [@7,9:9=' ',<7>,1:9], [@8,10:11='91',<2>,1:10]," +
			" [@9,12:12=' ',<7>,1:12], [@10,13:13='+',<5>,1:13], [@11,14:14=' ',<7>,1:14]," +
			" [@12,15:22='20234234',<2>,1:15], [@13,23:23=' ',<7>,1:23]," +
			" [@14,24:24='*',<6>,1:24], [@15,25:25=' ',<7>,1:25], [@16,26:26='0',<2>,1:26]," +
			" [@17,27:27=';',<3>,1:27], [@18,28:27='',<-1>,1:28]]";
		assertEquals(expecting, tokens.getTokens().toString());
    }
}
