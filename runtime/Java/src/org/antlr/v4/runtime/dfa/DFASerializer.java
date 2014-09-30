/*
 * [The "BSD license"]
 *  Copyright (c) 2012 Terence Parr
 *  Copyright (c) 2012 Sam Harwell
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.runtime.dfa;

import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfig;
import org.antlr.v4.runtime.atn.ATNSimulator;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.PredictionContext;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** A DFA walker that knows how to dump them to serialized strings. */
public class DFASerializer {
	@NotNull
	private final DFA dfa;
	@NotNull
	private final Vocabulary vocabulary;
	@Nullable
	final String[] ruleNames;
	@Nullable
	final ATN atn;

	/**
	 * @deprecated Use {@link #DFASerializer(DFA, Vocabulary)} instead.
	 */
	@Deprecated
	public DFASerializer(@NotNull DFA dfa, @Nullable String[] tokenNames) {
		this(dfa, VocabularyImpl.fromTokenNames(tokenNames), null, null);
	}

	public DFASerializer(@NotNull DFA dfa, @NotNull Vocabulary vocabulary) {
		this(dfa, vocabulary, null, null);
	}

	public DFASerializer(@NotNull DFA dfa, @Nullable Recognizer<?, ?> parser) {
		this(dfa,
			 parser != null ? parser.getVocabulary() : VocabularyImpl.EMPTY_VOCABULARY,
			 parser != null ? parser.getRuleNames() : null,
			 parser != null ? parser.getATN() : null);
	}

	/**
	 * @deprecated Use {@link #DFASerializer(DFA, Vocabulary, String[], ATN)} instead.
	 */
	@Deprecated
	public DFASerializer(@NotNull DFA dfa, @Nullable String[] tokenNames, @Nullable String[] ruleNames, @Nullable ATN atn) {
		this(dfa, VocabularyImpl.fromTokenNames(tokenNames), ruleNames, atn);
	}

	public DFASerializer(@NotNull DFA dfa, @NotNull Vocabulary vocabulary, @Nullable String[] ruleNames, @Nullable ATN atn) {
		this.dfa = dfa;
		this.vocabulary = vocabulary;
		this.ruleNames = ruleNames;
		this.atn = atn;
	}

	@Override
	public String toString() {
		if ( dfa.s0.get()==null ) return null;
		StringBuilder buf = new StringBuilder();

		if ( dfa.states!=null ) {
			List<DFAState> states = new ArrayList<DFAState>(dfa.states.values());
			Collections.sort(states, new Comparator<DFAState>(){

				@Override
				public int compare(DFAState o1, DFAState o2) {
					return o1.stateNumber - o2.stateNumber;
				}
			});

			for (DFAState s : states) {
				Map<Integer, DFAState> edges = s.getEdgeMap();
				Map<Integer, DFAState> contextEdges = s.getContextEdgeMap();
				for (Map.Entry<Integer, DFAState> entry : edges.entrySet()) {
					if ((entry.getValue() == null || entry.getValue() == ATNSimulator.ERROR) && !s.isContextSymbol(entry.getKey())) {
						continue;
					}

					boolean contextSymbol = false;
					buf.append(getStateString(s)).append("-").append(getEdgeLabel(entry.getKey())).append("->");
					if (s.isContextSymbol(entry.getKey())) {
						buf.append("!");
						contextSymbol = true;
					}

					DFAState t = entry.getValue();
					if ( t!=null && t.stateNumber != Integer.MAX_VALUE ) {
						buf.append(getStateString(t)).append('\n');
					}
					else if (contextSymbol) {
						buf.append("ctx\n");
					}
				}

				if (s.isContextSensitive()) {
					for (Map.Entry<Integer, DFAState> entry : contextEdges.entrySet()) {
						buf.append(getStateString(s))
							.append("-")
							.append(getContextLabel(entry.getKey()))
							.append("->")
							.append(getStateString(entry.getValue()))
							.append("\n");
					}
				}
			}
		}
		String output = buf.toString();
		if ( output.length()==0 ) return null;
		//return Utils.sortLinesInString(output);
		return output;
	}

	protected String getContextLabel(int i) {
		if (i == PredictionContext.EMPTY_FULL_STATE_KEY) {
			return "ctx:EMPTY_FULL";
		}
		else if (i == PredictionContext.EMPTY_LOCAL_STATE_KEY) {
			return "ctx:EMPTY_LOCAL";
		}

		if (atn != null && i > 0 && i <= atn.states.size()) {
			ATNState state = atn.states.get(i);
			int ruleIndex = state.ruleIndex;
			if (ruleNames != null && ruleIndex >= 0 && ruleIndex < ruleNames.length) {
				return "ctx:" + String.valueOf(i) + "(" + ruleNames[ruleIndex] + ")";
			}
		}

		return "ctx:" + String.valueOf(i);
	}

	protected String getEdgeLabel(int i) {
		return vocabulary.getDisplayName(i);
	}

	String getStateString(DFAState s) {
		if (s == ATNSimulator.ERROR) {
			return "ERROR";
		}

		int n = s.stateNumber;
		String stateStr = "s"+n;
		if ( s.isAcceptState() ) {
            if ( s.predicates!=null ) {
                stateStr = ":s"+n+"=>"+Arrays.toString(s.predicates);
            }
            else {
                stateStr = ":s"+n+"=>"+s.getPrediction();
            }
		}

		if ( s.isContextSensitive() ) {
			stateStr += "*";
			for (ATNConfig config : s.configs) {
				if (config.getReachesIntoOuterContext()) {
					stateStr += "*";
					break;
				}
			}
		}
		return stateStr;
	}
}
