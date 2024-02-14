package org.ifcopenshell;


/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.shared.exceptions.PluginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

public class IfcGeomServerClient implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(IfcGeomServerClient.class);

	private boolean calculateQuantities = false;
	private boolean applyLayersets = false;
	private boolean disableOpeningSubtractions = false;

	public boolean doesCalculateQuantities() {
		return calculateQuantities;
	}

	public void setCalculateQuantities(boolean calculateQuantities) throws RenderEngineException {
		if (process != null) {
			throw new RenderEngineException("Cannot be changed when running");
		}
		this.calculateQuantities = calculateQuantities;
	}

	public boolean doesApplyLayersets() {
		return applyLayersets;
	}

	public void setApplyLayersets(boolean applyLayersets) throws RenderEngineException {
		if (process != null) {
			throw new RenderEngineException("Cannot be changed when running");
		}
		this.applyLayersets = applyLayersets;
	}

	public boolean disablesOpeningSubtractions(){
		return disableOpeningSubtractions;
	}

	public void setDisableOpeningSubtractions(boolean disableOpeningSubtractions) throws RenderEngineException {
		if (process != null) {
			throw new RenderEngineException("Cannot be changed when running");
		}
		this.disableOpeningSubtractions = disableOpeningSubtractions;
	}

	private Process process = null;
	private LittleEndianDataInputStream dis = null;
	private LittleEndianDataOutputStream dos = null;
	private boolean hasMore = false;

	private volatile boolean running = true;

	private Path executableFilename;

	private GregorianCalendar buildDateTime;

	public Path getExecutableFilename() {
		return executableFilename;
	}

	@Override
	public void close() throws RenderEngineException {
		running = false;
		terminate();
	}

	private static String getOs() throws RenderEngineException {
		final String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows")) {
			return "win";
		} else if (os.contains("osx") || os.contains("os x") || os.contains("darwin") || os.contains("mac")) {
			return "osx";
		} else if (os.contains("linux")) {
			return "linux";
		} else {
			throw new RenderEngineException(String.format("IfcOpenShell is not available on the %s platorm", os));
		}
	}

	private static String getExecutableExtension() {
		final String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows")) {
			return ".exe";
		} else {
			return "";
		}
	}

	public static Path getExecutablePathFromRepo(Path root) throws PluginException {
		final String executableName = "IfcGeomServer" + getExecutableExtension();
		final String operatingSystem = getOs();
		final String bitness = operatingSystem.equals("osx") ? "64" : System.getProperty("sun.arch.data.model");

		return root.resolve("exe").resolve(bitness).resolve(operatingSystem).resolve(executableName);
	}

	private Path getSourcePath() throws RenderEngineException {
		String name = getClass().getName();
		int lastDot = name.lastIndexOf(".");
		name = name.substring(lastDot + 1) + ".class";
		try {
			return Paths.get(this.getClass().getResource(name).toURI()).getParent().getParent().getParent().getParent().getParent();
		} catch (URISyntaxException e) {
			throw new RenderEngineException(e);
		}
	}

	public enum ExecutableSource {
		REPOSITORY, S3
	}

	public IfcGeomServerClient(ExecutableSource source, String commitSha) throws RenderEngineException {
		getExecutable(source, commitSha, Paths.get(System.getProperty("user.home")));
	}

	public IfcGeomServerClient(ExecutableSource source, String commitSha, Path homeDir) throws RenderEngineException {
		getExecutable(source, commitSha, homeDir);
	}

	private void getExecutable(ExecutableSource source, String commitSha, Path homeDir) throws RenderEngineException {
		if (source == ExecutableSource.REPOSITORY) {
			try {
				this.executableFilename = getExecutablePathFromRepo(getSourcePath());
				FileTime fileTime = (FileTime) Files.getAttribute(this.executableFilename, "creationTime");
				buildDateTime = new GregorianCalendar();
				buildDateTime.setTimeInMillis(fileTime.toMillis());
			} catch (PluginException e) {
				throw new RenderEngineException(e);
			} catch (IOException e) {
				throw new RenderEngineException(e);
			}
		} else if (source == ExecutableSource.S3) {
			boolean initialized = false;
			
			String platform = getPlatform();
			try {
				String url = "https://s3.amazonaws.com/ifcopenshell-builds/IfcGeomServer-" + IfcOpenShellEnginePlugin.BRANCH + "-" + commitSha + "-" + platform + ".zip";
				
				String baseName = new File(new URL(url).getPath()).getName();
				baseName = baseName.substring(0, baseName.length() - 4);
				baseName += getExecutableExtension();
				this.executableFilename = homeDir.resolve(baseName);
				
				if (!Files.exists(this.executableFilename)) {
					LOGGER.info(String.format("Downloading from %s", url));
					Files.createDirectories(this.executableFilename.getParent());
					LOGGER.info(String.format("Unzipping to %s", this.executableFilename.toString()));
					
					try (CloseableHttpClient httpClient = HttpClients.custom().useSystemProperties().build()) {
						HttpGet httpGet = new HttpGet(url);
						try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
							if (httpResponse.getStatusLine().getStatusCode() == 200) {
								Header lastModified = httpResponse.getFirstHeader("Last-Modified");
								LOGGER.info("IfcOpenShell Last Modified: " + lastModified.getValue());
								buildDateTime = new GregorianCalendar();
								buildDateTime.setTime(DateUtils.parseDate(lastModified.getValue()));
								
								try (ZipInputStream zipInputStream = new ZipInputStream(httpResponse.getEntity().getContent())) {
									try (OutputStream fos = Files.newOutputStream(this.executableFilename)) {
										// tfk: assume single entry
										zipInputStream.getNextEntry();
										ByteStreams.copy(zipInputStream, fos);
									}
								}
								
								Files.setAttribute(this.executableFilename, "creationTime", FileTime.fromMillis(buildDateTime.getTimeInMillis()));
							} else {
								LOGGER.error(httpResponse.getStatusLine().toString());
								LOGGER.error("File not found " + url);
								throw new RenderEngineException("File not found " + url);
							}
						}
					}
					
					try {
						Set<PosixFilePermission> permissions = new HashSet<>();
						permissions.add(PosixFilePermission.OWNER_EXECUTE);
						permissions.add(PosixFilePermission.OWNER_READ);
						permissions.add(PosixFilePermission.OWNER_WRITE);
						Files.setPosixFilePermissions(this.executableFilename, permissions);
					} catch (Exception e) {}
				} else {
					FileTime fileTime = (FileTime) Files.getAttribute(this.executableFilename, "creationTime");
					buildDateTime = new GregorianCalendar();
					buildDateTime.setTimeInMillis(fileTime.toMillis());
				}
				
				initialized = true;
			} catch (IOException | PluginException e) {
				throw new RenderEngineException(e);
			}
			if (!initialized) {
				throw new RenderEngineException("No IfcGeomServer executable found for platform '" + platform + "'");
			}
		}
	}

	public IfcGeomServerClient(Path executableFilename) throws RenderEngineException {
		this.executableFilename = executableFilename;
	}

	public void initialize() throws RenderEngineException {
		try {
			process = Runtime.getRuntime().exec(new String[]{this.executableFilename.toAbsolutePath().toString()});
			dos = new LittleEndianDataOutputStream(process.getOutputStream());
			dis = new LittleEndianDataInputStream(process.getInputStream());

			if (dis.readInt() != HELLO) {
				LOGGER.error("Invalid welcome message received");
				terminate();
				return;
			}

			Hello h = new Hello();
			h.read(dis);
			
			new Setting(Setting.SettingId.CALCULATE_QUANTITITES, calculateQuantities).write(dos);
			new Setting(Setting.SettingId.APPLY_LAYERSETS, applyLayersets).write(dos);
			new Setting(Setting.SettingId.DISABLE_OPENING_SUBTRACTION, disableOpeningSubtractions).write(dos);
		} catch (IOException e) {
			throw new RenderEngineException(e);
		}
	}

	public void loadModel(InputStream inputStream) throws RenderEngineException {
		if (process == null) {
			initialize();
		}

		IfcModel m = new IfcModel(inputStream);
		try {
			m.write(dos);
			askForMore();
		} catch (IOException e) {
			close();
		}
	}

	public void loadModel(InputStream inputStream, long length) throws RenderEngineException {
		if (process == null) {
			initialize();
		}

		IfcModel m = new IfcModel(inputStream, length);
		try {
			m.write(dos);
			askForMore();
		} catch (IOException e) {
			close();
		}
	}

	private static final int HELLO = 0xff00;
	private static final int IFC_MODEL = HELLO + 1;
	private static final int GET = IFC_MODEL + 1;
	private static final int ENTITY = GET + 1;
	private static final int MORE = ENTITY + 1;
	private static final int NEXT = MORE + 1;
	private static final int BYE = NEXT + 1;
	private static final int GET_LOG = BYE + 1;
	private static final int LOG = GET_LOG + 1;
	private static final int DEFLECTION = LOG + 1;
	private static final int SETTING = DEFLECTION + 1;

	private static String VERSION = "IfcOpenShell-0.6.0b0-0";

	abstract static class Command {
		abstract void read_contents(LittleEndianDataInputStream s) throws IOException;

		abstract void write_contents(LittleEndianDataOutputStream s) throws IOException;

		int iden;
		int len;

		void read(LittleEndianDataInputStream s) throws IOException {
			len = s.readInt();
			read_contents(s);
		}

		void write(LittleEndianDataOutputStream s) throws IOException {
			s.writeInt(iden);
			ByteArrayOutputStream oss = new ByteArrayOutputStream();
			write_contents(new LittleEndianDataOutputStream(oss));

			// Comment Ruben: It seems redundant to send the size twice (when
			// sending a String, LittleEndianness should not change the size I
			// think)
			// Also storing the intermediate results in another buffer can be
			// avoided I think, why not send the original s variable to
			// write_contents?
			s.writeInt(oss.size());
			s.write(oss.toByteArray());
			s.flush();
		}

		Command(int iden) {
			this.iden = iden;
		}

		protected String readString(LittleEndianDataInputStream s) throws IOException {
			int len = s.readInt();
			byte[] b = new byte[len];
			s.readFully(b);
			String str = new String(b);
			while (len++ % 4 != 0)
				s.read();
			return str;
		}

		protected float[] readFloatArray(LittleEndianDataInputStream s) throws IOException {
			int len = s.readInt() / 4;
			float[] fs = new float[len];
			for (int i = 0; i < len; ++i) {
				fs[i] = s.readFloat();
			}
			return fs;
		}

		protected double[] readDoubleArray(LittleEndianDataInputStream s) throws IOException {
			int len = s.readInt() / 8;
			double[] fs = new double[len];
			for (int i = 0; i < len; ++i) {
				fs[i] = s.readDouble();
			}
			return fs;
		}

		protected ByteBuffer readByteBuffer(LittleEndianDataInputStream s) throws IOException {
			int byteLength = s.readInt();
			byte[] buffer = new byte[byteLength];
			s.readFully(buffer);
			return ByteBuffer.wrap(buffer);
		}

		// This method can be removed as soon as the binaries have been updated
		protected ByteBuffer readByteFloatToDoubleBuffer(LittleEndianDataInputStream s) throws IOException {
			int byteLength = s.readInt();
			byte[] buffer = new byte[byteLength];
			s.readFully(buffer);
			ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
			ByteBuffer newBuffer = ByteBuffer.allocate(buffer.length * 2);
			FloatBuffer floatBuffer = byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
			DoubleBuffer doubleBuffer = newBuffer.order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();
			int capacity = floatBuffer.capacity();
			for (int i=0; i<capacity; i++) {
				doubleBuffer.put(floatBuffer.get(i));
			}
			return newBuffer;
		}

		protected int[] readIntArray(LittleEndianDataInputStream s) throws IOException {
			int len = s.readInt() / 4;
			int[] is = new int[len];
			for (int i = 0; i < len; ++i) {
				is[i] = s.readInt();
			}
			return is;
		}

		protected void writeString(LittleEndianDataOutputStream s, String str) throws IOException {
			byte[] b = str.getBytes(Charsets.UTF_8);
			int len = b.length;
			s.writeInt(len);
			s.write(b);
			while (len++ % 4 != 0)
				s.write(0);
		}

		protected void writeStringBinary(LittleEndianDataOutputStream s, byte[] data) throws IOException {
			int len = data.length;
			s.writeInt(len);
			s.write(data);
			while (len++ % 4 != 0)
				s.write(0);
		}

		protected void writeStringBinary(LittleEndianDataOutputStream s, InputStream inputStream, int length) throws IOException {
			s.writeInt(length);
			IOUtils.copy(inputStream, s);
			while (length++ % 4 != 0)
				s.write(0);
		}
	}

	static class Hello extends Command {
		private String string;

		public String getString() {
			return string;
		}

		Hello() {
			super(HELLO);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			string = readString(s);
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
			throw new UnsupportedOperationException();
		}
	}

	static class More extends Command {
		private Boolean more;

		public Boolean hasMore() {
			return more;
		}

		More() {
			super(MORE);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			more = s.readInt() == 1;
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
			throw new UnsupportedOperationException();
		}
	}

	static class IfcModel extends Command {
		private InputStream ifcInputStream;
		private long length = -1;

		IfcModel(InputStream ifcInputStream) {
			super(IFC_MODEL);
			this.ifcInputStream = ifcInputStream;
		}

		IfcModel(InputStream ifcInputStream, long length) {
			super(IFC_MODEL);
			this.ifcInputStream = ifcInputStream;
			this.length = length;
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) throws IOException {
			if (length == -1) {
				// This is now the point where memory problems will arise for
				// large models
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				IOUtils.copy(ifcInputStream, baos);
				writeStringBinary(s, baos.toByteArray());
			} else {
				writeStringBinary(s, ifcInputStream, (int) length);
			}
		}
	}

	static class Get extends Command {
		Get() {
			super(GET);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
		}
	}

	static class Next extends Command {
		Next() {
			super(NEXT);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
		}
	}

	static class Bye extends Command {
		Bye() {
			super(BYE);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
		}
	}

	static class Entity extends Command {
		private IfcGeomServerClientEntity entity;

		Entity() {
			super(ENTITY);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s0) throws IOException {
			byte[] message = new byte[len];
			s0.readFully(message, 0, len);
			ByteArrayInputStream bis = new ByteArrayInputStream(message);
			LittleEndianDataInputStream s = new LittleEndianDataInputStream(bis);
			entity = new IfcGeomServerClientEntity(s.readInt(), readString(s), readString(s), readString(s), s.readInt(), readDoubleArray(s), s.readInt(), readByteBuffer(s), readByteBuffer(s),
					readByteBuffer(s), readByteBuffer(s), readByteBuffer(s), readRemainder(bis));
		}

		private String readRemainder(ByteArrayInputStream bis) {
			if (bis.available() == 0) {
				return null;
			}
			byte[] remainder = new byte[bis.available()];
			bis.read(remainder, 0, remainder.length);
			return new String(remainder);
		}

		public IfcGeomServerClientEntity getEntity() {
			return entity;
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
			throw new UnsupportedOperationException();
		}
	}

	static class GetLog extends Command {
		GetLog() {
			super(GET_LOG);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
		}
	}

	static class Log extends Command {
		private String string;

		Log() {
			super(LOG);
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			string = readString(s);
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) {
			throw new UnsupportedOperationException();
		}

		public String getString() {
			return string;
		}
	}

	static class Deflection extends Command {
		private double deflection;

		Deflection(double deflection) {
			super(DEFLECTION);
			this.deflection = deflection;
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) throws IOException {
			s.writeDouble(deflection);
		}
	}

	static class Setting extends Command {
		private int id;
		private int value;

		public enum SettingId {
			CALCULATE_QUANTITITES(1 << 4),
			APPLY_LAYERSETS (1 << 13),
			DISABLE_OPENING_SUBTRACTION(1 << 6);

			private final int id;

			SettingId(int id) {
				this.id = id;
			}

			private int getId() {
				return id;
			}
		}

		Setting(SettingId i, boolean b) {
			super(SETTING);
			this.id = i.getId();
			this.value = b ? 1 : 0;
		}

		@Override
		void read_contents(LittleEndianDataInputStream s) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		void write_contents(LittleEndianDataOutputStream s) throws IOException {
			s.writeInt(id);
			s.writeInt(value);
		}
	}

	private void terminate() throws RenderEngineException {
		hasMore = false;
		if (process == null)
			return;

		// Try and get the conversion log and say goodbye to the executable
		try {
			GetLog gl = new GetLog();
			gl.write(dos);

			if (dis.readInt() != LOG) {
				LOGGER.error("Invalid command sequence encountered");
				throw new IOException();
			}

			Log lg = new Log();
			lg.read(dis);

			final String log = lg.getString().trim();
			if (log.length() > 0) {
				LOGGER.info("\n" + log);
			}

			Bye b = new Bye();
			b.write(dos);

			if (dis.readInt() != BYE) {
				LOGGER.error("Invalid command sequence encountered");
				throw new IOException();
			}
			b.read(dis);
		} catch (Throwable e) {
		}

		try {
			// Give the executable some time to terminate by itself or kill
			// it after 2 seconds have passed
			for (int n = 0;;) {
				try {
					if (process.exitValue() != 0) {
						// LOGGER.error(String.format("Exited with non-zero exit
						// code: %d", process.exitValue()));
						throw new RenderEngineException(String.format("Exited with non-zero exit code: %d", process.exitValue()));
					}
					break;
				} catch (IllegalThreadStateException e) {
					if (n++ == 20) {
						process.destroy();
						LOGGER.error("Forcefully terminated IfcOpenShell process");
						break;
					}
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		} finally {
			process.destroyForcibly();
		}

		dis = null;
		dos = null;
		process = null;
	}

	private void askForMore() throws IOException {
		hasMore = false;
		if (dis.readInt() != MORE) {
			LOGGER.error("Invalid command sequence encountered");
			throw new IOException();
		}

		More mr = new More();
		mr.read(dis);

		hasMore = mr.hasMore();
	}

	public IfcGeomServerClientEntity getNext() throws RenderEngineException {
		try {
			Get g = new Get();
			g.write(dos);

			if (dis.readInt() != ENTITY) {
				LOGGER.error("Invalid command sequence encountered");
				throw new IOException();
			}
			Entity e = new Entity();
			e.read(dis);

			Next n = new Next();
			n.write(dos);

			askForMore();

			return e.getEntity();
		} catch (IOException e) {
			terminate();
			return null;
		}
	}

	public boolean isRunning() {
		return running;
	}

	public boolean hasNext() {
		return hasMore;
	}

	public String getVersion() {
		return VERSION;
	}

	public GregorianCalendar getBuildDateTime() {
		return buildDateTime;
	}
	
	public String getPlatform() throws RenderEngineException {
		String platform = "unknown";
		String os = getOs();
		
		if (os == "osx") {
			platform = "macos64";
		} else {
			platform = os.toLowerCase() + System.getProperty("sun.arch.data.model");
		}
		
		return platform;
	}
}