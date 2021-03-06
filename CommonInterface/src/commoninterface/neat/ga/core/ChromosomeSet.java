package commoninterface.neat.ga.core;

import java.util.ArrayList;

/**
 * @author MSimmerson
 *
 */
public class ChromosomeSet extends ArrayList<Chromosome> {
	private static final long serialVersionUID = 6716388429458077262L;
	private boolean isElitistSet;
	private int idx = 0;
	
	public ChromosomeSet() {
		this(false);
	}
	public ChromosomeSet(boolean isElitistSet) {
		this.isElitistSet = isElitistSet;
		this.idx = 0;
	}
	
	public boolean isElististSet() {
		return (this.isElitistSet);
	}
		
	public Chromosome nextChromosome() {
		if (this.idx >= this.size()) {
			this.idx = 0;
		}
		return (this.get(this.idx++));
	}
}
