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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.Nullable;

/**
 *
 * @author Sam Harwell
 */
public class ATNConfigSet implements Set<ATNConfig> {

	private final boolean localContext;
	private final Map<MergeKey, ATNConfig> mergedConfigs;
	private final List<ATNConfig> unmerged;
	private final List<ATNConfig> configs;

	public int outerContextDepth;

	private int uniqueAlt;
	private IntervalSet conflictingAlts;
	private boolean hasSemanticContext;
	private boolean dipsIntoOuterContext;

	public ATNConfigSet(boolean localContext) {
		this.localContext = localContext;
		this.mergedConfigs = new HashMap<MergeKey, ATNConfig>();
		this.unmerged = new ArrayList<ATNConfig>();
		this.configs = new ArrayList<ATNConfig>();

		this.uniqueAlt = ATN.INVALID_ALT_NUMBER;
	}

	private ATNConfigSet(ATNConfigSet set, boolean readonly) {
		this.localContext = set.localContext;
		if (readonly) {
			this.mergedConfigs = null;
			this.unmerged = null;
		} else {
			this.mergedConfigs = new HashMap<MergeKey, ATNConfig>(set.mergedConfigs);
			this.unmerged = new ArrayList<ATNConfig>(set.unmerged);
		}

		this.configs = new ArrayList<ATNConfig>(set.configs);

		this.outerContextDepth = set.outerContextDepth;
		this.dipsIntoOuterContext = set.dipsIntoOuterContext;
		this.hasSemanticContext = set.hasSemanticContext;
		this.uniqueAlt = set.uniqueAlt;
		this.conflictingAlts = set.conflictingAlts;
	}

	public boolean isLocalContext() {
		return localContext;
	}

	public boolean isReadOnly() {
		return mergedConfigs == null;
	}

	public Set<ATNState> getStates() {
		Set<ATNState> states = new HashSet<ATNState>();
		for (ATNConfig c : this.configs) {
			states.add(c.state);
		}

		return states;
	}

	public void optimizeConfigs(ATNSimulator interpreter) {
		if (configs.isEmpty()) {
			return;
		}

		for (int i = 0; i < configs.size(); i++) {
			ATNConfig config = configs.get(i);
			config.context = interpreter.getCachedContext(config.context);
		}
	}

	public ATNConfigSet clone(boolean readonly) {
		return new ATNConfigSet(this, readonly);
	}

	@Override
	public int size() {
		return configs.size();
	}

	@Override
	public boolean isEmpty() {
		return configs.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		if (!(o instanceof ATNConfig)) {
			return false;
		}

		ATNConfig config = (ATNConfig)o;
		ATNConfig mergedConfig = mergedConfigs.get(getKey(config));
		if (mergedConfig != null && canMerge(config, mergedConfig)) {
			return mergedConfig.contains(config);
		}

		for (ATNConfig c : unmerged) {
			if (c.contains(config)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Iterator<ATNConfig> iterator() {
		return new ATNConfigSetIterator();
	}

	@Override
	public Object[] toArray() {
		return configs.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return configs.toArray(a);
	}

	@Override
	public boolean add(ATNConfig e) {
		return add(e, null);
	}

	public boolean add(ATNConfig e, @Nullable PredictionContextCache contextCache) {
		ensureWritable();
		boolean added;
		boolean addKey;
		MergeKey key = getKey(e);
		ATNConfig mergedConfig = mergedConfigs.get(key);
		addKey = (mergedConfig == null);
		if (mergedConfig != null && canMerge(e, key, mergedConfig)) {
			mergedConfig.reachesIntoOuterContext = Math.max(mergedConfig.reachesIntoOuterContext, e.reachesIntoOuterContext);
			if (contextCache == null) {
				boolean localJoin = localContext || dipsIntoOuterContext || mergedConfig.reachesIntoOuterContext > 0;
				contextCache = localJoin ? PredictionContextCache.UNCACHED_LOCAL : PredictionContextCache.UNCACHED_FULL;
			}

			PredictionContext joined = PredictionContext.join(mergedConfig.context, e.context, contextCache);
			if (mergedConfig.context == joined) {
				return false;
			}

			mergedConfig.context = joined;
			updatePropertiesForMergedConfig(e);
			return true;
		}

		for (int i = 0; i < unmerged.size(); i++) {
			ATNConfig unmergedConfig = unmerged.get(i);
			if (canMerge(e, key, unmergedConfig)) {
				unmergedConfig.reachesIntoOuterContext = Math.max(unmergedConfig.reachesIntoOuterContext, e.reachesIntoOuterContext);
				if (contextCache == null) {
					boolean localJoin = localContext || dipsIntoOuterContext || unmergedConfig.reachesIntoOuterContext > 0;
					contextCache = localJoin ? PredictionContextCache.UNCACHED_LOCAL : PredictionContextCache.UNCACHED_FULL;
				}

				PredictionContext joined = PredictionContext.join(unmergedConfig.context, e.context, contextCache);
				if (unmergedConfig.context == joined) {
					return false;
				}

				unmergedConfig.context = joined;

				if (addKey) {
					mergedConfigs.put(key, unmergedConfig);
					unmerged.remove(i);
				}

				updatePropertiesForMergedConfig(e);
				return true;
			}
		}

		added = true;

		if (added) {
			configs.add(e);
			if (addKey) {
				mergedConfigs.put(key, e);
			} else {
				unmerged.add(e);
			}

			updatePropertiesForAddedConfig(e);
		}

		return added;
	}

	private void updatePropertiesForMergedConfig(ATNConfig config) {
		// merged configs can't change the alt or semantic context
		dipsIntoOuterContext |= config.reachesIntoOuterContext > 0;
	}

	private void updatePropertiesForAddedConfig(ATNConfig config) {
		if (configs.size() == 1) {
			uniqueAlt = config.alts.getSingleElement();
		} else if (uniqueAlt != config.alts.getSingleElement()) {
			uniqueAlt = ATN.INVALID_ALT_NUMBER;
		}

		hasSemanticContext |= !SemanticContext.NONE.equals(config.semanticContext);
		dipsIntoOuterContext |= config.reachesIntoOuterContext > 0;
	}

	private static boolean canMerge(ATNConfig left, ATNConfig right) {
		if (getKey(left) != getKey(right)) {
			return false;
		}

		return left.semanticContext.equals(right.semanticContext);
	}

	private static boolean canMerge(ATNConfig left, MergeKey leftKey, ATNConfig right) {
		if (left.state.stateNumber != right.state.stateNumber) {
			return false;
		}

		if (!leftKey.equals(getKey(right))) {
			return false;
		}

		return left.semanticContext.equals(right.semanticContext);
	}

	private static MergeKey getKey(ATNConfig e) {
		return new MergeKey(e.state.stateNumber, e.alts);
	}

	@Override
	public boolean remove(Object o) {
		ensureWritable();

		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c) {
			if (!(o instanceof ATNConfig)) {
				return false;
			}

			if (!contains((ATNConfig)o)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean addAll(Collection<? extends ATNConfig> c) {
		return addAll(c, null);
	}

	public boolean addAll(Collection<? extends ATNConfig> c, PredictionContextCache contextCache) {
		ensureWritable();

		boolean changed = false;
		for (ATNConfig group : c) {
			changed |= add(group, contextCache);
		}

		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		ensureWritable();
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		ensureWritable();
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void clear() {
		ensureWritable();

		mergedConfigs.clear();
		unmerged.clear();
		configs.clear();

		outerContextDepth = 0;

		dipsIntoOuterContext = false;
		hasSemanticContext = false;
		uniqueAlt = ATN.INVALID_ALT_NUMBER;
		conflictingAlts = null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ATNConfigSet)) {
			return false;
		}

		ATNConfigSet other = (ATNConfigSet)obj;
		return this.localContext == other.localContext
			&& configs.equals(other.configs);
	}

	@Override
	public int hashCode() {
		int hashCode = 1;
		hashCode = 5 * hashCode + (localContext ? 1 : 0);
		hashCode = 5 * hashCode + configs.hashCode();
		return hashCode;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean showContext) {
		StringBuilder buf = new StringBuilder();
		List<ATNConfig> sortedConfigs = new ArrayList<ATNConfig>(configs);
		Collections.sort(sortedConfigs, new Comparator<ATNConfig>() {
			@Override
			public int compare(ATNConfig o1, ATNConfig o2) {
				if (!o1.alts.equals(o2.alts)) {
					assert o1.alts.size() == 1 && o2.alts.size() == 1;
					return o1.alts.getMinElement() - o2.alts.getMinElement();
				}
				else if (o1.state.stateNumber != o2.state.stateNumber) {
					return o1.state.stateNumber - o2.state.stateNumber;
				}
				else {
					return o1.semanticContext.toString().compareTo(o2.semanticContext.toString());
				}
			}
		});

		buf.append("[");
		for (int i = 0; i < sortedConfigs.size(); i++) {
			if (i > 0) {
				buf.append(", ");
			}
			buf.append(sortedConfigs.get(i).toString(null, true, true));
		}
		buf.append("]");

		if ( hasSemanticContext ) buf.append(",hasSemanticContext="+hasSemanticContext);
		if ( uniqueAlt!=ATN.INVALID_ALT_NUMBER ) buf.append(",uniqueAlt="+uniqueAlt);
		if ( conflictingAlts!=null ) buf.append(",conflictingAlts="+conflictingAlts);
		if ( dipsIntoOuterContext ) buf.append(",dipsIntoOuterContext");
		return buf.toString();
	}

	public int getUniqueAlt() {
		return uniqueAlt;
	}

	public boolean hasSemanticContext() {
		return hasSemanticContext;
	}

	public IntervalSet getConflictingAlts() {
		return conflictingAlts;
	}

	public void setConflictingAlts(IntervalSet conflictingAlts) {
		//ensureWritable(); <-- these do end up set after the DFAState is created, but set to a distinct value
		this.conflictingAlts = conflictingAlts;
	}

	public boolean getDipsIntoOuterContext() {
		return dipsIntoOuterContext;
	}

	public ATNConfig get(int index) {
		return configs.get(index);
	}

	public void remove(int index) {
		ensureWritable();
		ATNConfig config = configs.get(index);
		configs.remove(config);
		MergeKey key = getKey(config);
		if (mergedConfigs.get(key) == config) {
			mergedConfigs.remove(key);
		} else {
			for (int i = 0; i < unmerged.size(); i++) {
				if (unmerged.get(i) == config) {
					unmerged.remove(i);
					return;
				}
			}
		}
	}

	protected final void ensureWritable() {
		if (isReadOnly()) {
			throw new IllegalStateException("This ATNConfigSet is read only.");
		}
	}

	private static final class MergeKey {
		private final int state;
		@NotNull
		private final IntervalSet alts;

		public MergeKey(int state, @NotNull IntervalSet alts) {
			assert alts != null && !alts.isNil();
			this.state = state;
			this.alts = alts;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof MergeKey)) {
				return false;
			}

			if (obj == this) {
				return true;
			}

			MergeKey other = (MergeKey)obj;
			return this.state == other.state
				&& this.alts.equals(other.alts);
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 71 * hash + this.state;
			hash = 71 * hash + this.alts.hashCode();
			return hash;
		}
	}

	private final class ATNConfigSetIterator implements Iterator<ATNConfig> {

		int index = -1;
		boolean removed = false;

		@Override
		public boolean hasNext() {
			return index + 1 < configs.size();
		}

		@Override
		public ATNConfig next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			index++;
			removed = false;
			return configs.get(index);
		}

		@Override
		public void remove() {
			if (removed || index < 0 || index >= configs.size()) {
				throw new IllegalStateException();
			}

			ATNConfigSet.this.remove(index);
			removed = true;
		}

	}
}
