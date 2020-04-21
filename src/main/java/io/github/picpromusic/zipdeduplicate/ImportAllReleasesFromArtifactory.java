package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ImportAllReleasesFromArtifactory {

	private static final String ZERO_HASH = "0000000000000000000000000000000000000000";

	public static void main(String[] args)
			throws SAXException, IOException, ParserConfigurationException, InterruptedException {
		URL url = new URL(args[0]);
		Path tempDir = Files.createTempDirectory("ImportAll");
		tempDir.toFile().deleteOnExit();

		ExecutorService threadPoolDownload = Executors.newFixedThreadPool(2);
		ExecutorService threadPoolInsert = Executors.newSingleThreadExecutor();

		List<MvnRepoMetadata> all = loadMetaData(//
				url, //
				args[1], //
				args[2], //
				v -> "releases/" + args[2] + "/" + v + ":" + ZERO_HASH)//
//						.limit(1)//
						.collect(Collectors.toList());

		AtomicLong al =new AtomicLong(0);
		
		all.forEach(m -> threadPoolDownload.submit(() -> {
			Path versionTempDir = tempDir.resolve(m.version);
			try {
				Files.createDirectories(versionTempDir);
				versionTempDir.toFile().deleteOnExit();
				Path destPath = versionTempDir.resolve(m.artifactId + ".zip");
				destPath.toFile().deleteOnExit();
				System.out.println("Downloading " + m.url);
				Files.copy(m.url.openStream(), destPath);
				al.addAndGet(Files.size(destPath));
				threadPoolInsert.submit(() -> {
					while (true) {
						try {
							System.out.println("Import " + destPath.toString());
							List<String> parameters = new ArrayList<>();
							parameters.add("-noPush");
							parameters.add("c:\\devel\\zipdedup.local.git");
							parameters.add(destPath.toString());
							parameters.add(m.repo);
							parameters.add(m.additionalInfo);
							BulkInsert.main(parameters.toArray(new String[0]));
							break;
						} catch (GitAPIException | IOException e) {
							e.printStackTrace();
						}
					}
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}));

		threadPoolDownload.shutdown();
		threadPoolDownload.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		threadPoolInsert.shutdown();
		threadPoolInsert.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		System.out.println("Downloadsize: " + al.get());
	}

	private static Stream<MvnRepoMetadata> loadMetaData(URL url, String repo, String artifactId,
			Function<String, String> additionalInfoFunc)
			throws SAXException, IOException, ParserConfigurationException {
		URL metadataURL = resolve(url, "maven-metadata.xml");
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();

		Document document = builder.parse(metadataURL.openStream());
		Element documentElement = document.getDocumentElement();
		Element versioning = getChildElementStream(documentElement, "versioning").findFirst().get();
		Element versions = getChildElementStream(versioning, "versions").findFirst().get();

		return getChildElementStream(versions, "version")//
				.map(Element::getTextContent)//
				.map(v -> {
					URL resolve = resolve(resolve(url, v), artifactId + "-" + v + ".zip");
					return new MvnRepoMetadata(artifactId, v, resolve, repo, additionalInfoFunc.apply(v));
				});
	}

	private static URL resolve(URL url, String pathElement) {
		StringBuilder external = new StringBuilder(url.toExternalForm());
		if (external.charAt(external.length() - 1) != '/') {
			external.append('/');
		}
		external.append(pathElement);
		try {
			return new URL(external.toString());
		} catch (MalformedURLException e) {
			throw new RuntimeException();
		}
	}

	private static Stream<Element> getChildElementStream(Element element, String nodeName) {
		NodeList childNodes = element.getChildNodes();
		return IntStream.range(0, childNodes.getLength())//
				.mapToObj(childNodes::item)//
				.filter(Element.class::isInstance)//
				.map(Element.class::cast)//
				.filter(e -> e.getNodeName().equals(nodeName));
	}

	public static class MvnRepoMetadata {

		public final URL url;
		public final String repo;
		public final String additionalInfo;
		public final String artifactId;
		public final String version;

		public MvnRepoMetadata(String artifactId, String version, URL url, String repo, String additionalInfo) {
			this.artifactId = artifactId;
			this.url = url;
			this.repo = repo;
			this.additionalInfo = additionalInfo;
			this.version = version;
		}

		@Override
		public String toString() {
			return url.toExternalForm();
		}

	}
}
