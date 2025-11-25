package com.pmd.test;

import java.util.*;
import java.util.logging.Logger;

public class PMDBreaker {
	// Violations:
	// - Unused fields
	// - Non-final static field
	// - Variable naming rules
	public static int BAD_field = 5;
	public int x = 0;
	private String s;
	private Logger log = Logger.getLogger("PMDBreaker"); // triggers GuardLogStatement if enabled

	// Magic number violation, LongMethod violation
	public PMDBreaker() {
		x = 42;
		s = "hello";

		if (x == 42) { } // Empty if
	}

	// Lots of violations:
	// - Long method
	// - Cognitive complexity
	// - NPath complexity
	// - Useless String operations
	// - Unnecessary local variables
	// - Switch fallthrough
	// - Duplicate code
	public void doBadStuff(int a) {
		int i = 0; // unused
		String z = "abc" + "def"; // unnecessary concatenation

		if (a == 1) {
			log.info("A == 1"); // log without guard
		} else if (a == 2) {
			log.info("A == 2");
		} else if (a == 3) {
			log.info("A == 3");
		} else if (a == 4) {
			log.info("A == 4");
		} else {
			log.info("A == something");
		}

		switch (a) {
			case 1:
				System.out.println("one");
			case 2: // FALLTHROUGH
				System.out.println("two");
			default:
				System.out.println("default");
		}

		// Bad loops, useless logic
		for (int j = 0; j < 10; j++) {
			for (int k = 0; k < 5; k++) {
				if (j == k) {
					System.out.println("equal");
				}
			}
		}

		// Empty catch, empty finally
		try {
			String n = null;
			n.length(); // NPE
		} catch (Exception e) {
		} finally {
		}

		// Duplicate code
		if (a > 0) {
			log.info("duplicate");
		}
		if (a > 0) {
			log.info("duplicate");
		}
	}
}

