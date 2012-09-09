/*
 * [The "BSD license"]
 *  Copyright (c) 2012 Sam Harwell
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *  1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *      derived from this software without specific prior written permission.
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
package org.antlr.v4.runtime.atn;

import org.antlr.v4.runtime.atn.PredictionContextCache.IdentityCommutativePredictionContextOperands;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

public class ArrayPredictionContext extends PredictionContext {

	@NotNull
	public final PredictionContext[] parents;
	@NotNull
	public final int[] invokingStates;

	/*package*/ ArrayPredictionContext(@NotNull PredictionContext[] parents, int[] invokingStates) {
		super(calculateHashCode(calculateParentHashCode(parents), calculateInvokingStatesHashCode(invokingStates)));
		assert parents.length == invokingStates.length;
		assert invokingStates.length > 1 || invokingStates[0] != EMPTY_FULL_STATE_KEY : "Should be using PredictionContext.EMPTY instead.";

		this.parents = parents;
		this.invokingStates = invokingStates;
	}

	/*package*/ ArrayPredictionContext(@NotNull PredictionContext[] parents, int[] invokingStates, int hashCode) {
		super(hashCode);
		assert parents.length == invokingStates.length;
		assert invokingStates.length > 1 || invokingStates[0] != EMPTY_FULL_STATE_KEY : "Should be using PredictionContext.EMPTY instead.";

		this.parents = parents;
		this.invokingStates = invokingStates;
	}

	@Override
	public PredictionContext getParent(int index) {
		return parents[index];
	}

	@Override
	public int getInvokingState(int index) {
		return invokingStates[index];
	}

	@Override
	public int findInvokingState(int invokingState) {
		return Arrays.binarySearch(invokingStates, invokingState);
	}

	@Override
	public int size() {
		return invokingStates.length;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean hasEmpty() {
		return invokingStates[invokingStates.length - 1] == EMPTY_FULL_STATE_KEY;
	}

	@Override
	protected PredictionContext addEmptyContext() {
		if (hasEmpty()) {
			return this;
		}

		PredictionContext[] parents2 = Arrays.copyOf(parents, parents.length + 1);
		int[] invokingStates2 = Arrays.copyOf(invokingStates, invokingStates.length + 1);
		parents2[parents2.length - 1] = PredictionContext.EMPTY_FULL;
		invokingStates2[invokingStates2.length - 1] = PredictionContext.EMPTY_FULL_STATE_KEY;
		return new ArrayPredictionContext(parents2, invokingStates2);
	}

	@Override
	public PredictionContext appendContext(PredictionContext suffix, PredictionContextCache contextCache) {
		return appendContext(this, suffix, new IdentityHashMap<PredictionContext, PredictionContext>());
	}

	private static PredictionContext appendContext(PredictionContext context, PredictionContext suffix, IdentityHashMap<PredictionContext, PredictionContext> visited) {
		if (suffix.isEmpty()) {
			if (isEmptyLocal(suffix)) {
				if (context.hasEmpty()) {
					return EMPTY_LOCAL;
				}
				
				throw new UnsupportedOperationException("what to do here?");
			}

			return context;
		}

		if (suffix.size() != 1) {
			throw new UnsupportedOperationException("Appending a tree suffix is not yet supported.");
		}

		PredictionContext result = visited.get(context);
		if (result == null) {
			if (context.isEmpty()) {
				result = suffix;
			}
			else {
				int parentCount = context.size();
				if (context.hasEmpty()) {
					parentCount--;
				}

				PredictionContext[] updatedParents = new PredictionContext[parentCount];
				int[] updatedInvokingStates = new int[parentCount];
				for (int i = 0; i < parentCount; i++) {
					updatedInvokingStates[i] = context.getInvokingState(i);
				}

				for (int i = 0; i < parentCount; i++) {
					updatedParents[i] = appendContext(context.getParent(i), suffix, visited);
				}

				if (updatedParents.length == 1) {
					result = new SingletonPredictionContext(updatedParents[0], updatedInvokingStates[0]);
				}
				else {
					assert updatedParents.length > 1;
					result = new ArrayPredictionContext(updatedParents, updatedInvokingStates);
				}

				if (context.hasEmpty()) {
					result = PredictionContext.join(result, suffix);
				}
			}

			visited.put(context, result);
		}

		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		else if (!(o instanceof ArrayPredictionContext)) {
			return false;
		}

		if ( this.hashCode() != o.hashCode() ) {
			return false; // can't be same if hash is different
		}

		ArrayPredictionContext other = (ArrayPredictionContext)o;
		return equals(other, new HashSet<IdentityCommutativePredictionContextOperands>());
	}

	private boolean equals(ArrayPredictionContext other, Set<IdentityCommutativePredictionContextOperands> visited) {
		Deque<PredictionContext> selfWorkList = new ArrayDeque<PredictionContext>();
		Deque<PredictionContext> otherWorkList = new ArrayDeque<PredictionContext>();
		selfWorkList.push(this);
		otherWorkList.push(other);
		while (!selfWorkList.isEmpty()) {
			IdentityCommutativePredictionContextOperands operands = new IdentityCommutativePredictionContextOperands(selfWorkList.pop(), otherWorkList.pop());
			if (!visited.add(operands)) {
				continue;
			}

			int selfSize = operands.getX().size();
			if (selfSize == 0) {
				if (!operands.getX().equals(operands.getY())) {
					return false;
				}

				continue;
			}

			int otherSize = operands.getY().size();
			if (selfSize != otherSize) {
				return false;
			}

			for (int i = 0; i < selfSize; i++) {
				if (operands.getX().getInvokingState(i) != operands.getY().getInvokingState(i)) {
					return false;
				}

				PredictionContext selfParent = operands.getX().getParent(i);
				PredictionContext otherParent = operands.getY().getParent(i);
				if (selfParent.hashCode() != otherParent.hashCode()) {
					return false;
				}

				if (selfParent != otherParent) {
					selfWorkList.push(selfParent);
					otherWorkList.push(otherParent);
				}
			}
		}

		return true;
	}

}
