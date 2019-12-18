package jardeduplicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

public class TestDefaultZipInfoCollector {

	@Test
	public void testEmpty() {
		DefaultZipInfoCollector defaultZipInfoCollector = new DefaultZipInfoCollector();
		List<String> allZipPathes = defaultZipInfoCollector.getAllZipPathes();
		assertEquals(0, allZipPathes.size());
	}
	@Test
	public void testSimple() {
		DefaultZipInfoCollector defaultZipInfoCollector = new DefaultZipInfoCollector();
		defaultZipInfoCollector.newZipFile(Paths.get("abc/def.ear"));
		List<String> allZipPathes = defaultZipInfoCollector.getAllZipPathes();
		assertTrue(allZipPathes.toString(), allZipPathes.contains("abc/def.ear"));
		assertEquals(1, allZipPathes.size());
	}

	@Test
	public void testSimpleWithResolve() {
		DefaultZipInfoCollector defaultZipInfoCollector = new DefaultZipInfoCollector();
		ZipInfoCollector zipInfoCollector = defaultZipInfoCollector.resolve(Paths.get("abc"));
		zipInfoCollector.newZipFile(Paths.get("def.ear"));
		List<String> allZipPathes = defaultZipInfoCollector.getAllZipPathes();
		assertTrue(allZipPathes.toString(), allZipPathes.contains("abc/def.ear"));
		assertEquals(1, allZipPathes.size());
	}

	@Test
	public void testSimpleWithDoubleResolve() {
		DefaultZipInfoCollector defaultZipInfoCollector = new DefaultZipInfoCollector();
		ZipInfoCollector zipInfoCollector = defaultZipInfoCollector.resolve(Paths.get("abc"));
		zipInfoCollector = zipInfoCollector.resolve(Paths.get("xyz"));
		zipInfoCollector.newZipFile(Paths.get("def.ear"));
		List<String> allZipPathes = defaultZipInfoCollector.getAllZipPathes();
		assertTrue(allZipPathes.toString(), allZipPathes.contains("abc/xyz/def.ear"));
		assertEquals(1, allZipPathes.size());
	}

	@Test
	public void testDeepZipWithDoubleResolve() {
		DefaultZipInfoCollector defaultZipInfoCollector = new DefaultZipInfoCollector();
		ZipInfoCollector zipInfoCollector = defaultZipInfoCollector.resolve(Paths.get("abc"));
		zipInfoCollector = zipInfoCollector.resolve(Paths.get("xyz"));
		zipInfoCollector.newZipFile(Paths.get("deep/deeper/deepest/def.ear"));
		List<String> allZipPathes = defaultZipInfoCollector.getAllZipPathes();
		assertTrue(allZipPathes.toString(), allZipPathes.contains("abc/xyz/deep/deeper/deepest/def.ear"));
		assertEquals(1, allZipPathes.size());
	}

}
