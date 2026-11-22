package life.shizune.mistinn.test;

/** Lets a run pick one test class with {@code -Dmistinn.only=SimpleClassName} (build.gradle: -PonlyTest). */
final class TestFilter {
	private TestFilter() {
	}

	static boolean skip(final Class<?> test) {
		String only = System.getProperty("mistinn.only");
		return only != null && !only.isBlank() && !only.equals(test.getSimpleName());
	}
}
