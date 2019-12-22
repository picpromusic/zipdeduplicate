package jardeduplicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestBulkImport extends AbstractGitTest {

	private static final boolean DELETE_ON_END = true;
	Git git;
	private BulkInsert bulkInsert;
	private Path tmpDir;
	private Path tmpDirFiles;
	private Path sourceFile;
	private DefaultZipInfoCollector zipInfo;

	@Before
	public void createGit() throws IOException, IllegalStateException, GitAPIException {
		tmpDir = Files.createTempDirectory(TestBulkImport.class.getName());
		Path tmpDirGit = tmpDir.resolve("test.git");
		tmpDirFiles = tmpDir.resolve("temp");
		Files.createDirectories(tmpDirFiles);
		sourceFile = tmpDirFiles.resolve("source.zip");
		if (!DELETE_ON_END) {
			System.out.println(tmpDir);
		}
		git = Git.init().setBare(true).setDirectory(tmpDirGit.toFile()).call();
		bulkInsert = new BulkInsert(git.getRepository(), BulkInsert.onlyThisExtensions(Arrays.asList(".zip")));
		zipInfo = new DefaultZipInfoCollector();
	}

	@After
	public void deleteGit() throws IOException {
		git.close();
		if (DELETE_ON_END) {
			deleteRecursive(tmpDir);
		}
	}

	@Test
	public void testSimple() throws IOException, GitAPIException {
		Files.write(sourceFile, new String("simple").getBytes());

		ObjectId treeId = bulkInsert.doIt(sourceFile, zipInfo);

		TreeWalk rootTw = createTreeWalk(git, treeId);

		assertHasNext(rootTw);
		assertFalse(rootTw.isSubtree());
		assertEquals("source.zip", rootTw.getPathString());
		List<String> all = readAll(git, rootTw.getObjectId(0));
		assertEquals(1, all.size());
		assertEquals("simple", all.get(0));

		assertHasNoNext(rootTw);

		assertTrue(zipInfo.getAllZipPathes().isEmpty());
	}

	@Test
	public void testSimpleZip() throws IOException, GitAPIException {
		OutputStream out = Files.newOutputStream(sourceFile);
		ZipOutputStream zout = new ZipOutputStream(out);
		zout.putNextEntry(new ZipEntry("test"));
		zout.write(new String("simpleZip").getBytes());
		zout.close();

		ObjectId treeId = bulkInsert.doIt(sourceFile, zipInfo);

		TreeWalk rootTw = createTreeWalk(git, treeId);
		assertHasNext(rootTw);
		assertTrue(rootTw.isSubtree());
		assertEquals("source.zip", rootTw.getPathString());

		// ANFANG source.zip
		TreeWalk sourceZipTw = createTreeWalk(git, rootTw.getObjectId(0));
		assertHasNext(sourceZipTw);
		assertFalse(sourceZipTw.isSubtree());
		assertEquals("test",sourceZipTw.getPathString());
		List<String> readAll = readAll(git, sourceZipTw.getObjectId(0));
		assertEquals(1,readAll.size());
		assertEquals("simpleZip",readAll.get(0));
		assertHasNoNext(sourceZipTw);
		// ENDE source.zip
		
		assertHasNoNext(rootTw);

		assertTrue(zipInfo.getAllZipPathes().contains("source.zip"));
	}

	@Test
	public void testSimpleZipWithTwoFiles() throws IOException, GitAPIException {
		OutputStream out = Files.newOutputStream(sourceFile);
		ZipOutputStream zout = new ZipOutputStream(out);
		zout.putNextEntry(new ZipEntry("test2.zip"));  // Nur der Name ist .zip
		zout.write(new String("simpleZip2").getBytes());
		zout.putNextEntry(new ZipEntry("test"));
		zout.write(new String("simpleZip").getBytes());
		zout.close();

		ObjectId treeId = bulkInsert.doIt(sourceFile, zipInfo);

		TreeWalk rootTw = createTreeWalk(git, treeId);
		assertHasNext(rootTw);
		assertTrue(rootTw.isSubtree());
		assertEquals("source.zip", rootTw.getPathString());

		// ANFANG source.zip
		TreeWalk sourceZipTw = createTreeWalk(git, rootTw.getObjectId(0));
		assertHasNext(sourceZipTw);
		assertFalse(sourceZipTw.isSubtree());
		assertEquals("test",sourceZipTw.getPathString());
		List<String> readAll = readAll(git, sourceZipTw.getObjectId(0));
		assertEquals(1,readAll.size());
		assertEquals("simpleZip",readAll.get(0));
		
		assertHasNext(sourceZipTw);
		assertFalse(sourceZipTw.isSubtree());
		assertEquals("test2.zip",sourceZipTw.getPathString());
		readAll = readAll(git, sourceZipTw.getObjectId(0));
		assertEquals(1,readAll.size());
		assertEquals("simpleZip2",readAll.get(0));

		assertHasNoNext(sourceZipTw);
		// ENDE source.zip
		
		assertHasNoNext(rootTw);

		assertEquals(1,zipInfo.getAllZipPathes().size());
		assertTrue(zipInfo.getAllZipPathes().contains("source.zip"));
	}

	@Test
	public void testInnerZip() throws IOException, GitAPIException {
		OutputStream out = Files.newOutputStream(sourceFile);
		ZipOutputStream zout = new ZipOutputStream(out);
		zout.putNextEntry(new ZipEntry("test2"));

		ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
		ZipOutputStream zout2 = new ZipOutputStream(zipBytes);
		zout2.putNextEntry(new ZipEntry("innerEntry"));
		zout2.write(new String("innerContent").getBytes());
		zout2.close();
		zout.write(zipBytes.toByteArray());
		zout.closeEntry();

		zout.putNextEntry(new ZipEntry("test"));
		zout.write(new String("simpleZip").getBytes());
		zout.closeEntry();
		zout.close();

		ObjectId treeId = bulkInsert.doIt(sourceFile, zipInfo);

		TreeWalk rootTw = createTreeWalk(git, treeId);
		assertHasNext(rootTw);
		assertTrue(rootTw.isSubtree());
		assertEquals("source.zip", rootTw.getPathString());

		// ANFANG source.zip
		TreeWalk sourceZipTw = createTreeWalk(git, rootTw.getObjectId(0));
		assertHasNext(sourceZipTw);
		assertFalse(sourceZipTw.isSubtree());
		assertEquals("test",sourceZipTw.getPathString());
		List<String> readAll = readAll(git, sourceZipTw.getObjectId(0));
		assertEquals(1,readAll.size());
		assertEquals("simpleZip",readAll.get(0));
		
		assertHasNext(sourceZipTw);
		assertFalse(sourceZipTw.isSubtree());
		assertEquals("test2",sourceZipTw.getPathString());

		assertHasNoNext(sourceZipTw);
		// ENDE source.zip
		
		assertHasNoNext(rootTw);

		assertEquals(1,zipInfo.getAllZipPathes().size());
		assertTrue(zipInfo.getAllZipPathes().contains("source.zip"));
	}

	@Test
	public void testInnerZipWithMatchingExtension() throws IOException, GitAPIException {
		OutputStream out = Files.newOutputStream(sourceFile);
		ZipOutputStream zout = new ZipOutputStream(out);
		zout.putNextEntry(new ZipEntry("test2.zip"));

		ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
		ZipOutputStream zout2 = new ZipOutputStream(zipBytes);
		zout2.putNextEntry(new ZipEntry("innerEntry"));
		zout2.write(new String("innerContent").getBytes());
		zout2.close();
		zout.write(zipBytes.toByteArray());
		zout.closeEntry();

		zout.putNextEntry(new ZipEntry("test"));
		zout.write(new String("simpleZip").getBytes());
		zout.closeEntry();
		zout.close();

		ObjectId treeId = bulkInsert.doIt(sourceFile, zipInfo);

		TreeWalk rootTw = createTreeWalk(git, treeId);
		assertHasNext(rootTw);
		assertTrue(rootTw.isSubtree());
		assertEquals("source.zip", rootTw.getPathString());

		// ANFANG source.zip
		TreeWalk sourceZipTw = createTreeWalk(git, rootTw.getObjectId(0));
		assertHasNext(sourceZipTw);
		assertFalse(sourceZipTw.isSubtree());
		assertEquals("test",sourceZipTw.getPathString());
		List<String> readAll = readAll(git, sourceZipTw.getObjectId(0));
		assertEquals(1,readAll.size());
		assertEquals("simpleZip",readAll.get(0));
		
		assertHasNext(sourceZipTw);
		assertTrue(sourceZipTw.isSubtree());
		assertEquals("test2.zip",sourceZipTw.getPathString());
		TreeWalk innerZipTw = createTreeWalk(git, sourceZipTw.getObjectId(0));
		assertHasNext(innerZipTw);
		assertFalse(innerZipTw.isSubtree());
		assertEquals("innerEntry",innerZipTw.getPathString());
		readAll = readAll(git, innerZipTw.getObjectId(0));
		assertEquals(1,readAll.size());
		assertEquals("innerContent",readAll.get(0));
		assertHasNoNext(innerZipTw);
		

		assertHasNoNext(sourceZipTw);
		// ENDE source.zip
		
		assertHasNoNext(rootTw);

		assertEquals(2,zipInfo.getAllZipPathes().size());
		assertTrue(zipInfo.getAllZipPathes().contains("source.zip"));
		assertTrue(zipInfo.getAllZipPathes().toString(),zipInfo.getAllZipPathes().contains("source.zip/test2.zip"));
	}
	
}
