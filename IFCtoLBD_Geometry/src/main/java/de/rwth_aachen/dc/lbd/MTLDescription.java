package de.rwth_aachen.dc.lbd;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/*
 *  Copyright (c) 2026 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class MTLDescription {

	private final List<MTLMaterial> materials = new ArrayList<>();

	public void addMaterial(MTLMaterial material) {
		this.materials.add(material);
	}

	public List<MTLMaterial> getMaterials() {
		return Collections.unmodifiableList(this.materials);
	}

	public String toMTLString() {
		StringBuilder sb = new StringBuilder();
		for (MTLMaterial material : this.materials) {
			sb.append(material.toMTLString()).append("\n");
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return Base64.getEncoder().encodeToString(toMTLString().getBytes());
	}

	public static class MTLMaterial {
		private final String name;
		private final double[] ka;
		private final double[] kd;
		private final double[] ks;
		private final double alpha;

		public MTLMaterial(String name, double[] ka, double[] kd, double[] ks, double alpha) {
			this.name = name;
			this.ka = ka;
			this.kd = kd;
			this.ks = ks;
			this.alpha = alpha;
		}

		public String getName() {
			return this.name;
		}

		public double[] getKa() {
			return this.ka.clone();
		}

		public String getKaColorString() {
			return toColorString(this.ka);
		}

		public double[] getKd() {
			return this.kd.clone();
		}

		public String getKdColorString() {
			return toColorString(this.kd);
		}

		public double[] getKs() {
			return this.ks.clone();
		}

		public String getKsColorString() {
			return toColorString(this.ks);
		}

		public double getAlpha() {
			return this.alpha;
		}

		public String getAlphaColorString() {
			return toColorString(new double[] { this.alpha, this.alpha, this.alpha });
		}

		private String toMTLString() {
			return String.format(Locale.ROOT,
					"newmtl %s\nKa %.6f %.6f %.6f\nKd %.6f %.6f %.6f\nKs %.6f %.6f %.6f\nd %.6f\nillum 2\n",
					this.name, this.ka[0], this.ka[1], this.ka[2], this.kd[0], this.kd[1], this.kd[2],
					this.ks[0], this.ks[1], this.ks[2], this.alpha);
		}

		private static String toColorString(double[] color) {
			return String.format(Locale.ROOT, "#%02x%02x%02x", toColorChannel(color[0]), toColorChannel(color[1]),
					toColorChannel(color[2]));
		}

		private static int toColorChannel(double value) {
			if (Double.isNaN(value)) {
				return 0;
			}
			return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
		}
	}
}
