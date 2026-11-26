package life.shizune.mistinn.test;

/** Lets a run pick its test classes with {@code -Dmistinn.only=SimpleClassName[,Another]} (build.gradle: -PonlyTest). */
final class TestFilter {
	private TestFilter() {
	}

	static boolean skip(final Class<?> test) {
		String only = System.getProperty("mistinn.only");
		return only != null && !only.isBlank() && !java.util.List.of(only.split(",")).contains(test.getSimpleName());
	}
}
