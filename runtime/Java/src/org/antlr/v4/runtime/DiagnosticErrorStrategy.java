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

import org.antlr.v4.runtime.atn.ATNConfig;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.PredictionContext;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.atn.SemanticContext;
import org.antlr.v4.runtime.atn.SimulatorState;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.Arrays;

public class DiagnosticErrorStrategy<Symbol extends Token> extends DefaultErrorStrategy<Symbol> {
    @Override
    public <T extends Symbol> void reportAmbiguity(@NotNull Parser<T> recognizer,
								DFA dfa, int startIndex, int stopIndex, @NotNull IntervalSet ambigAlts,
								@NotNull ATNConfigSet configs)
    {
        recognizer.notifyErrorListeners("reportAmbiguity d=" + dfa.decision + ": ambigAlts=" + ambigAlts + ":" + configs.toString(true) + ", input='" +
										recognizer.getInputString(startIndex, stopIndex) + "'");
    }

	@Override
	public <T extends Symbol> void reportAttemptingFullContext(@NotNull Parser<T> recognizer,
											@NotNull DFA dfa,
											int startIndex, int stopIndex,
											@NotNull SimulatorState<T> initialState)
	{
		recognizer.notifyErrorListeners("reportAttemptingFullContext d=" + dfa.decision + ": " + getFullContextConfigs(initialState).toString(true) + ", input='" +
										recognizer.getInputString(startIndex, stopIndex) + "'");
	}

	@Override
	public <T extends Symbol> void reportContextSensitivity(@NotNull Parser<T> recognizer, @NotNull DFA dfa,
                                         int startIndex, int stopIndex, @NotNull SimulatorState<T> acceptState)
    {
        recognizer.notifyErrorListeners("reportContextSensitivity d=" + dfa.decision + ": " + getFullContextConfigs(acceptState).toString(true) + ", input='" +
										recognizer.getInputString(startIndex, stopIndex) + "'");
    }

    @Override
    public <T extends Symbol> void reportInsufficientPredicates(@NotNull Parser<T> recognizer,
											 @NotNull DFA dfa,
											 int startIndex, int stopIndex,
											 @NotNull IntervalSet ambigAlts,
											 DecisionState decState,
											 @NotNull SemanticContext[] altToPred,
											 @NotNull ATNConfigSet configs, boolean fullContextParse)
    {
        recognizer.notifyErrorListeners("reportInsufficientPredicates d=" + dfa.decision + ", decState=" + decState +
										", ambigAlts=" + ambigAlts + ":" + Arrays.toString(altToPred) +
										", " + configs.toString(true) + ", input='" + recognizer.getInputString(startIndex, stopIndex) + "'");
    }

	protected static ATNConfigSet getFullContextConfigs(SimulatorState<?> state) {
		ATNConfigSet configs = new ATNConfigSet(false);
		PredictionContext suffix = PredictionContext.fromRuleContext(state.remainingOuterContext);
		for (ATNConfig config : state.s0.configset) {
			configs.add(config.appendContext(suffix, PredictionContextCache.UNCACHED_FULL));
		}

		return configs;
	}

}
