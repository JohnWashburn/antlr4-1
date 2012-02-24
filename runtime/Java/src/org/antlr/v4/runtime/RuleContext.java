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
package org.antlr.v4.runtime;

import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.tree.gui.TreeViewer;

import javax.print.PrintException;
import java.io.IOException;

/** A rule context is a record of a single rule invocation. It knows
 *  which context invoked it, if any. If there is no parent context, then
 *  naturally the invoking state is not valid.  The parent link
 *  provides a chain upwards from the current rule invocation to the root
 *  of the invocation tree, forming a stack. We actually carry no
 *  information about the rule associated with this context (except
 *  when parsing). We keep only the state number of the invoking state from
 *  the ATN submachine that invoked this. Contrast this with the s
 *  pointer inside ParserRuleContext that tracks the current state
 *  being "executed" for the current rule.
 *
 *  The parent contexts are useful for computing lookahead sets and
 *  getting error information.
 *
 *  These objects are used during lexing, parsing, and prediction.
 *  For the special case of parsers and tree parsers, we use the subclass
 *  ParserRuleContext.
 *
 *  @see ParserRuleContext
 */
public class RuleContext<Symbol> implements ParseTree.RuleNode<Symbol> {
	/** What context invoked this rule? */
	public RuleContext<Symbol> parent;

	/** What state invoked the rule associated with this context?
	 *  The "return address" is the followState of invokingState
	 *  If parent is null, this should be -1.
	 */
	public int invokingState = -1;

	public RuleContext() {}

	public RuleContext(RuleContext<Symbol> parent, int invokingState) {
		this.parent = parent;
		//if ( parent!=null ) System.out.println("invoke "+stateNumber+" from "+parent);
		this.invokingState = invokingState;
	}

	public static <T> RuleContext<T> getChildContext(RuleContext<T> parent, int invokingState) {
		return new RuleContext<T>(parent, invokingState);
	}

	public int depth() {
		int n = 0;
		RuleContext<?> p = this;
		while ( p!=null ) {
			p = p.parent;
			n++;
		}
		return n;
	}

	/** A context is empty if there is no invoking state; meaning nobody call
	 *  current context.
	 */
	public boolean isEmpty() {
		return invokingState == -1;
	}

	// satisfy the ParseTree interface

	@Override
	public RuleContext<Symbol> getRuleContext() { return this; }

	@Override
	public ParseTree<Symbol> getParent() { return parent; }

	@Override
	public RuleContext<Symbol> getPayload() { return this; }

	public int getRuleIndex() { return -1; }

	@Override
	public ParseTree<Symbol> getChild(int i) {
		return null;
	}

	@Override
	public int getChildCount() {
		return 0;
	}

	@Override
	public Interval getSourceInterval() {
		if ( getChildCount()==0 ) return Interval.INVALID;
		int start = getChild(0).getSourceInterval().a;
		int stop = getChild(getChildCount()-1).getSourceInterval().b;
		return new Interval(start, stop);
	}

	public void inspect(Parser<?> parser) {
		TreeViewer viewer = new TreeViewer(parser, this);
		viewer.open();
	}

	public void save(Parser<?> parser, String fileName)
		throws IOException, PrintException
	{
		Trees.writePS(this, parser, fileName);
	}

	public void save(Parser<?> parser, String fileName,
					 String fontName, int fontSize)
		throws IOException
	{
		Trees.writePS(this, parser, fileName, fontName, fontSize);
	}

	/** Print out a whole tree, not just a node, in LISP format
	 *  (root child1 .. childN). Print just a node if this is a leaf.
	 *  We have to know the recognizer so we can get rule names.
	 */
	public String toStringTree(Parser<?> recog) {
		return Trees.toStringTree(this, recog);
	}

	@Override
	public String toStringTree() { return toStringTree(null); }

	@Override
	public String toString() {
		return toString(null);
	}

	public String toString(@Nullable Recognizer<?, ?> recog) {
		return toString(recog, ParserRuleContext.emptyContext());
	}

	// recog null unless ParserRuleContext, in which case we use subclass toString(...)
	public String toString(@Nullable Recognizer<?,?> recog, RuleContext<?> stop) {
		StringBuilder buf = new StringBuilder();
		RuleContext<?> p = this;
		buf.append("[");
		while ( p != null && p != stop ) {
			if ( !p.isEmpty() ) buf.append(p.invokingState);
			if ( p.parent != null && !p.parent.isEmpty() ) buf.append(" ");
			p = p.parent;
		}
		buf.append("]");
		return buf.toString();
	}
}
