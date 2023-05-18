import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class LatticeHexBackup {
	private int[][] lattice;
	private double[][] growth;
	private double[][] delta;
	private int[][] age;
	private double[][] dTable;

	private double deltaFar = 0.5;
	private double alphaFacet = 1;
	private double alphaKink = 1;
	private double deltaX = 0.0002;
	private double X0 = 0.0001;
	private double tbMin = 0.00025;

	private int time;
	private double maxDist;
	private double minDistCryst = 9999;
	private double minDist = 9999;
	private int size;

	private double maxSeedError = 2E-5;
	private double maxError = 1E-4;

	private String testName;

	private int iceQ = 0;

	public enum Seed {
		SQUARE, TRIANGLE, HEX, TILTED_SQUARE, CIRCLE, LINE, STAR, SPIRAL;
	}

	public static void main(String[] args) {// { 1, 1, 1 }, { 0.75, 1, 1 }, { 0.5, 1, 1 }, { 1, 0.9, 1 },
		// { 1, 0.8, 1 }, { 1, 0.7, 1 }, { 1, 0.6, 1 }, { 1, 0.5, 1 }, { 1, 0.4, 1 }, {
		// 1, 0.3, 1 }, { 1, 0.2, 1 },
		// { 1, 1.1, 1 }, { 1, 1.2, 1 }, { 1, 1.3, 1 }, { 1, 1.4, 1 }, { 1, 1.5, 1 }, {
		// 0.75, 0.8, 1 },
		// { 0.5, 0.8, 1 }, { 0.75, 0.6, 1 }, { 0.5, 0.6, 1 }, { 0.75, 0.4, 1 }, { 0.5,
		// 0.4, 1 },
		double[][] params = new double[][] { { 1, 1, 1 }, { 2, 1, 1 }, { 3, 1, 1 } };

		int s = 150;

		for (double[] param : params) {
			LatticeHexBackup lh = new LatticeHexBackup(s);

			lh.setX0(0.00005);
			lh.setDeltaFar(param[0]);
			lh.setAlphaFacet(param[1]);
			lh.setAlphaKink(param[2]);

			lh.seed(Seed.HEX, 9);
			lh.runCapture(s / 8, 25, false);
		}
	}

	public LatticeHexBackup(int size, String testName) {
		this.testName = testName;
		int w = size * 3 / 2, h = size;
		this.size = size;
		this.lattice = new int[w][h];
		this.growth = new double[w][h];
		this.delta = new double[w][h];
		this.dTable = new double[w][h];
		this.age = new int[w][h];
	}

	public LatticeHexBackup(int size) {
		this(size, null);
	}

	private double dist(int x, int y) {
		double dx = lattice.length * 2 / 3 - x - y / 2, dy = (lattice[0].length / 2 - y) * (Math.sqrt(3) / 2);
		return Math.sqrt(dx * dx + dy * dy);
	}

	private void runCapture(int iter, int cap, boolean b) {
		save("Step 0");
		for (int i = 0; b ? i < iter / cap : iter > maxDist; i++) {
			System.out.println("Step " + (i + 1) + "/" + (iter / cap + 1));
			run(cap);
			save("Step " + (i + 1));
			System.out.println(maxDist);
		}
	}

	private void run(int i) {
		for (int x = 0; x < i; x++) {
			int q = 0;
			while (updateDelta() > maxError)
				q++;
			System.out.println("\t" + q + " updates. Run " + x);
			updateGrowth();
			updateCells();
		}
	}

	private void seed(Seed format, int size) {
		this.testName = "3dF=" + deltaFar + ", aF=" + alphaFacet + ", aK=" + alphaKink;
		for (int x = 0; x < lattice.length; x++)
			for (int y = 0; y < lattice[0].length; y++) {
				delta[x][y] = deltaFar;// deltaFar * (1 - 0.35 * Math.exp(-0.0654 * dist(x, y) * size / 300));
				dTable[x][y] = dist(x, y);
			}
		switch (format) {
		case CIRCLE:
			break;
		case HEX:
			int xo = lattice.length / 2, yo = lattice[0].length / 2;
			lattice[xo][yo] = 1;
			lattice[xo + 1][yo] = 1;
			lattice[xo - 1][yo] = 1;
			lattice[xo - 1][yo + 1] = 1;
			lattice[xo][yo + 1] = 1;
			lattice[xo + 1][yo - 1] = 1;
			lattice[xo][yo - 1] = 1;
			iceQ += 7;
			break;
		case LINE:
			break;
		case SPIRAL:
			break;
		case SQUARE:
			break;
		case STAR:
			break;
		case TILTED_SQUARE:
			break;
		case TRIANGLE:
			break;
		default:
			break;
		}
		maxDist = size;
		updateCells();
		while (updateDelta() > maxSeedError) {
		}
		for (int i = 0; i < delta.length; i += (i > 180 ? 2 : i > 120 ? 10 : 50)) {
			// System.out.println((delta.length / 2 - i) + "\t" + delta[i][delta[0].length /
			// 2]);
		}
	}

	private void updateGrowth() {
		int[][] latt = clone(this.lattice);
		double del = tbMin / deltaX;
		double tolerance = 0.98;
		this.minDist = 9999;
		this.minDistCryst = 9999;
		for (int y = 1; y < lattice[0].length - 1; y++)
			for (int x = (size - y) / 2 + 1; x < (size - y) / 2 + size; x++) {
				// if (dTable[x][y] > maxDist)
				// continue;
				int ice = count(x, y, 1);
				if (lattice[x][y] == 2) {
					if (dTable[x][y] > maxDist)
						maxDist = dTable[x][y];
					growth[x][y] += (ice == 1 ? alphaFacet : ice == 2 ? alphaKink * Math.sqrt(2) : 0) * del
							* delta[x][y];
					if (growth[x][y] >= 1) {
						latt[x][y] = 1;
						iceQ++;
					}
					if (minDistCryst > dTable[x][y])
						minDistCryst = dTable[x][y];
				} else if (lattice[x][y] == 0)
					if (Math.abs(delta[x][y] / deltaFar - 1) <= 1 - tolerance && minDist > dTable[x][y])
						minDist = dTable[x][y];
			}
		lattice = latt;
	}

	private void updateCells() {
		int[][] latt = clone(this.lattice);
		for (int y = 1; y < lattice[0].length - 1; y++)
			for (int x = (size - y) / 2 + 1; x < (size - y) / 2 + size; x++) {
				// if (dTable[x][y] > maxDist)
				// continue;
				int ice = count(x, y, 1);
				if (lattice[x][y] == 0 && ice > 0)
					latt[x][y] = 2;
				if (lattice[x][y] == 1)
					age[x][y]++;
				if (lattice[x][y] == 2 && ice >= 4)
					latt[x][y] = 1;
			}
		lattice = latt;
	}

	private double updateDelta() {
		double[][] delta0 = clone(this.delta);
		double del = deltaX / X0;
		double maxDelta = 0;
		for (int y = 1; y < lattice[0].length - 1; y++)
			for (int x = (size - y) / 2 + 1; x < (size - y) / 2 + size; x++) {
				if (dTable[x][y] > minDist * 1.1 || (iceQ > 50 && dTable[x][y] < (minDistCryst - 2) * 0.9))
					continue;
				if (lattice[x][y] == 0) {
					int vapor = count(x, y, 0), bound = count(x, y, 2);
					delta0[x][y] = (delta[x - 1][y] + delta[x - 1][y + 1] + delta[x][y - 1] + delta[x][y + 1]
							+ delta[x + 1][y - 1] + delta[x + 1][y]) / (bound + vapor);
				} else if (lattice[x][y] == 2) {
					int ice = count(x, y, 1), vapor = count(x, y, 0);
					double d = ((lattice[x - 1][y] == 0 ? delta[x - 1][y] : 0)
							+ (lattice[x - 1][y + 1] == 0 ? delta[x - 1][y + 1] : 0)
							+ (lattice[x][y - 1] == 0 ? delta[x][y - 1] : 0)
							+ (lattice[x + 1][y - 1] == 0 ? delta[x + 1][y - 1] : 0)
							+ (lattice[x][y + 1] == 0 ? delta[x][y + 1] : 0)
							+ (lattice[x + 1][y] == 0 ? delta[x + 1][y] : 0)) / vapor;
					if (ice == 1) {
						delta0[x][y] = d / (1 + alphaFacet * del);
					} else if (ice == 2) {
						delta0[x][y] = d / (1 + alphaKink * del / Math.sqrt(2));
					}
				}
				double c = Math.abs(delta[x][y] - delta0[x][y]);
				if (c > maxDelta)
					maxDelta = c;
			}
		this.delta = delta0;
		return maxDelta;
	}

	public int count(int x, int y, int n) {
		return (lattice[x - 1][y] == n ? 1 : 0) + (lattice[x - 1][y + 1] == n ? 1 : 0)
				+ (lattice[x][y - 1] == n ? 1 : 0) + (lattice[x][y + 1] == n ? 1 : 0)
				+ (lattice[x + 1][y - 1] == n ? 1 : 0) + (lattice[x + 1][y] == n ? 1 : 0);
	}

	// Utility
	private double[][] clone(double[][] delta) {
		double[][] n = new double[delta.length][delta[0].length];
		for (int y = 0; y < delta[0].length; y++)
			for (int x = 0; x < delta.length; x++)
				n[x][y] = delta[x][y];
		return n;
	}

	private int[][] clone(int[][] lattice) {
		int[][] n = new int[lattice.length][lattice[0].length];
		for (int y = 0; y < lattice[0].length; y++)
			for (int x = 0; x < lattice.length; x++)
				n[x][y] = lattice[x][y];
		return n;
	}

	public void save(String name) {
		int r = 12;
		BufferedImage bi = new BufferedImage((int) (lattice.length * r * 2f / 3),
				(int) (lattice[0].length * r * Math.sqrt(3) / 2), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) bi.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		for (int y = 0; y < lattice[0].length; y++) {
			for (int x = 0; x < lattice.length; x++) {
				int c = (int) (Math.min(255 * (Math.log10(delta[x][y] + 1) * 3), 255));
				g.setColor(lattice[x][y] == 0 || lattice[x][y] == 2 ? new Color(c, c, c)
						: (new Color((int) (255 * (Math.exp(-age[x][y] / 100f))),
								(int) (255 * Math.exp(-age[x][y] / 800f)),
								(int) (255 * (Math.exp(-age[x][y] / 1500f))))));
				drawHex(x - (int) (1d / 3 * lattice.length), y, g, r);
				// drawHex(x, y, g, r);
			}
		}
		g.dispose();
		try {
			File f = new File(testName);
			if (!f.exists())
				f.mkdir();
			ImageIO.write(bi, "jpg", new File(testName + "/" + name + ".jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void drawHex(int x, int y, Graphics2D g, int r) {
		g.fillOval((int) (r * (x + 0.5 * y) - (r + 1) * 0.5), (int) (r * y * Math.sqrt(3) / 2 - (r + 1) / 2), r + 4,
				r + 4);
	}

	public static BufferedImage resize(BufferedImage img, int newW, int newH) {
		Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
		BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2d = dimg.createGraphics();
		g2d.drawImage(tmp, 0, 0, null);
		g2d.dispose();

		return dimg;
	}

	public double getDeltaFar() {
		return deltaFar;
	}

	public void setDeltaFar(double deltaFar) {
		this.deltaFar = deltaFar;
	}

	public double getAlphaFacet() {
		return alphaFacet;
	}

	public void setAlphaFacet(double alphaFacet) {
		this.alphaFacet = alphaFacet;
	}

	public double getAlphaKink() {
		return alphaKink;
	}

	public void setAlphaKink(double alphaKink) {
		this.alphaKink = alphaKink;
	}

	public double getDeltaX() {
		return deltaX;
	}

	public void setDeltaX(double deltaX) {
		this.deltaX = deltaX;
	}

	public double getX0() {
		return X0;
	}

	public void setX0(double x0) {
		X0 = x0;
	}

	public double getTbMin() {
		return tbMin;
	}

	public void setTbMin(double tbMin) {
		this.tbMin = tbMin;
	}

	public double getMaxSeedError() {
		return maxSeedError;
	}

	public void setMaxSeedError(double maxSeedError) {
		this.maxSeedError = maxSeedError;
	}

	public double getMaxError() {
		return maxError;
	}

	public void setMaxError(double maxError) {
		this.maxError = maxError;
	}
}
