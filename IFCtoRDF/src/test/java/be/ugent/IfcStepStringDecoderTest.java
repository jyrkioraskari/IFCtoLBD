package be.ugent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IfcStepStringDecoderTest {
	@Test
	public void decodesMixedUnicodeScriptsAndKeepsAsciiText() {
		assertEquals("Ympäristö", IfcStepStringDecoder.decode("Ymp\\X2\\00E4\\X0\\rist\\X2\\00F6\\X0\\"));
		assertEquals("中文", IfcStepStringDecoder.decode("\\X2\\4E2D6587\\X0\\"));
		assertEquals("Привет", IfcStepStringDecoder.decode("\\X2\\041F04400438043204350442\\X0\\"));
		assertEquals("Élève français", IfcStepStringDecoder.decode("\\X2\\00C9006C00E8007600650020006600720061006E00E7006100690073\\X0\\"));
		assertEquals("Größe", IfcStepStringDecoder.decode("Gr\\X2\\00F600DF\\X0\\e"));
		assertEquals("😀", IfcStepStringDecoder.decode("\\X4\\0001F600\\X0\\"));
		assertEquals("plain ASCII", IfcStepStringDecoder.decode("plain ASCII"));
	}

	@Test
	public void decodesUtf16SurrogatePairInX2Sequence() {
		assertEquals("😀", IfcStepStringDecoder.decode("\\X2\\D83DDE00\\X0\\"));
	}
}
