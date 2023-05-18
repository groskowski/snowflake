import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringJoiner;

import javax.imageio.ImageIO;

public class LatticeHex {
	private int[][] lattice;
	private double[][] growth;
	private double[][] delta;
	private int[][] age;
	private double[][] dTable;

	private double deltaFar = 0.5;
	private double alphaFacet = 1;
	private double alphaKink = 1;
	private double deltaX = 0.0002;
	private double X0 = 0.0004;// greater->farther view
	private double tbMin = 0.00025;

	private int time;
	private double maxDist;
	private double minDistCryst = 9999;
	private double minDist = 9999;
	private int size;

	private double maxSeedError = 1E-3;
	private double maxError = 1E-3;

	private String testName;
	public int thread;

	private int iceQ = 0;

	public enum Seed {
		SQUARE, TRIANGLE, HEX, TILTED_SQUARE, CIRCLE, LINE, STAR, SPIRAL;
	}

	private volatile static int t = 0, next = 0;

	public static void main(String[] args) throws InterruptedException {
		// double[] sat = { 0.05, 0.1, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 2.5, 3, 5 };
		// double[] fac = { 0.001, 0.01, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.65, 0.8, 1,
		// 1.1, 1.25, 1.5, 2 };
		double[] sat = { 20 };
		double[] fac = { 0.001, 0.01, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.65, 0.8, 1, 1.1, 1.25, 1.5, 2 };

		double[][] params = new double[sat.length * fac.length][3];
		int i = 0;
		for (double sa : sat)
			for (double fa : fac)
				params[i++] = new double[] { sa, fa, 1 };

		System.out.println("Tests: " + params.length);

		int s = 600;
		i = 0;

		while (true) {
			if (t < 4 && params.length > next) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						double[] param = params[next++];

						System.out.println("Test " + t);
						LatticeHex lh = new LatticeHex(s);

						lh.setX0(0.00005);
						lh.setTbMin(0.0005);
						lh.setDeltaFar(param[0]);
						lh.setAlphaFacet(param[1]);
						lh.thread = next;
						lh.setAlphaKink(param[2]);

						lh.seed(Seed.HEX, 9);
						lh.runCapture(s / 8, 10, false);
						System.out.println(lh.thread + " Finished");
						t--;
					}
				}).start();
				t++;
			}
			Thread.sleep(1000);
		}
	}

	public LatticeHex(int size, String testName) {
		this.testName = testName;
		int w = size * 3 / 2, h = size;
		this.size = size;
		this.lattice = new int[w][h];
		this.growth = new double[w][h];
		this.delta = new double[w][h];
		this.dTable = new double[w][h];
		this.age = new int[w][h];
	}

	public LatticeHex(int size) {
		this(size, null);
	}

	private double dist(int x, int y) {
		double dx = lattice.length * 2 / 3 - x - y / 2 - 0.25, dy = (lattice[0].length / 2 - y) * (Math.sqrt(3) / 2);
		return Math.sqrt(dx * dx + dy * dy);
	}

	private void runCapture(int iter, int cap, boolean b) {
		long l = System.currentTimeMillis();
		makeDirs();
		storeInfo("crystals/" + testName + "/data/info");
		save("Step 0");
		for (int i = 0; b ? i < iter / cap : iter > maxDist; i++) {
			System.out.println("Thread " + thread + " - Step " + (i + 1));
			run(cap, b);
			save("Step " + (i + 1));
			if (System.currentTimeMillis() - l > 1000 * 1800) {
				System.out.println("ABORTED");
				break;
			}
		}
	}

	private void run(int i, boolean b) {
		double maxDist = this.maxDist;
		for (int x = 0; b ? x < i : this.maxDist < (maxDist + 1) * 1.1; x++) {
			int q = 0;
			while (updateDelta() > maxError)
				q++;
			// System.out.println("\t" + q + " updates. Run " + x);
			updateGrowth();
			updateCells();
		}
	}

	private void seed(Seed format, int size) {
		this.testName = "size=" + this.size + ", dF=" + deltaFar + ", aF=" + alphaFacet + ", aK=" + alphaKink;
		for (int y0 = 0; y0 < lattice[0].length; y0++)
			for (int x0 = (this.size - y0) / 2; x0 < (3 * this.size - y0) / 2; x0++) {
				int x = x0 > delta.length - 1 ? delta.length - 1 : x0, y = y0;
				delta[x][y] = deltaFar;
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
		// maxDist = size;
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
		double tolerance = 0.95;
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
					if (Math.abs(delta[x][y] / deltaFar - 1) <= 1 - tolerance && minDist > dTable[x][y]) {
						minDist = dTable[x][y];
						if (minDist > this.size / 2 / 1.1 - 5)
							minDist = this.size / 2 / 1.1 - 5;
					}
			}
		lattice = latt;
	}

	private void updateCells() {
		int[][] latt = clone(this.lattice);
		for (int e = 0; e < 2; e++)
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
		for (int y0 = 1; y0 < lattice[0].length - 1; y0++)
			for (int x0 = (this.size - y0) / 2 + 1; x0 < (3 * this.size - y0) / 2 - 1; x0++) {
				int x = x0 > delta.length - 1 ? delta.length - 1 : x0, y = y0;
				// if (dTable[x][y] > minDist * 1.1 || (iceQ > 50 && dTable[x][y] <
				// (minDistCryst - 2) * 0.9))
				// continue;
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

	public void save1(String name) {
		int r = 12;
		BufferedImage bi = new BufferedImage((int) ((lattice.length - 2) * r * 2f / 3),
				(int) (lattice[0].length * r * Math.sqrt(3) / 2), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) bi.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		for (int y = 0; y < lattice[0].length; y++) {
			for (int x = 0; x < lattice.length; x++) {
				int c = (int) (Math.min(255 * (Math.log10(delta[x][y] + 1) * 3), 255));
				g.setColor(lattice[x][y] == 0 || lattice[x][y] == 2 ? new Color(c, c, c)
						: (new Color((int) (55 * (Math.exp(-age[x][y] / 15000f)) + 200 * (Math.exp(-age[x][y] / 100f))),
								(int) (55 * (Math.exp(-age[x][y] / 15000f)) + 200 * Math.exp(-age[x][y] / 800f)),
								(int) (55 + 200 * (Math.exp(-age[x][y] / 1500f))))));
				drawHex(x - (int) (1d / 3 * lattice.length), y, g, r);
				// drawHex(x, y, g, r);
			}
		}
		g.dispose();
		try {
			String testName = "crystals/" + this.testName;
			ImageIO.write(bi, "jpg", new File(testName + "/gallery/" + name + ".jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void save(String name) {
		save1("a " + name);
		save2("b " + name);
		save4("c " + name);
		save3(name);
	}

	private void save4(String name) {
		int r = 12;
		BufferedImage bi = new BufferedImage((int) ((lattice.length - 2) * r * 2f / 3),
				(int) (lattice[0].length * r * Math.sqrt(3) / 2), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = (Graphics2D) bi.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		for (int y = 0; y < lattice[0].length; y++) {
			for (int x = 0; x < lattice.length; x++) {
				g.setColor(lattice[x][y] == 0 || lattice[x][y] == 2 ? Color.BLACK
						: (new Color((int) (55 * (Math.exp(-age[x][y] / 15000f)) + 200 * (Math.exp(-age[x][y] / 100f))),
								(int) (100 * (Math.exp(-age[x][y] / 15000f)) + 155 * Math.exp(-age[x][y] / 800f)),
								(int) (105 + 150 * (Math.exp(-age[x][y] / 1500f))))));
				drawHex(x - (int) (1d / 3 * lattice.length), y, g, r);
				// drawHex(x, y, g, r);
			}
		}
		g.dispose();
		try {
			String testName = "crystals/" + this.testName;
			ImageIO.write(bi, "jpg", new File(testName + "/gallery/" + name + ".jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void save3(String name) {
		String testName = "crystals/" + this.testName;
		storeArray(delta, testName + "/data/" + name + "_delta");
		storeArray(lattice, testName + "/data/" + name + "_lattice");
		storeArray(growth, testName + "/data/" + name + "_growth");
		storeArray(age, testName + "/data/" + name + "_age");
	}

	private void save2(String name) {
		int r = 12;
		BufferedImage bi = new BufferedImage((int) ((lattice.length - 2) * r * 2f / 3),
				(int) (lattice[0].length * r * Math.sqrt(3) / 2), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) bi.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		for (int y = 0; y < lattice[0].length; y++) {
			for (int x = 0; x < lattice.length; x++) {
				g.setColor(lattice[x][y] == 0 || lattice[x][y] == 2 ? Color.BLACK
						: (new Color((int) (55 * (Math.exp(-age[x][y] / 15000f)) + 200 * (Math.exp(-age[x][y] / 100f))),
								(int) (100 * (Math.exp(-age[x][y] / 15000f)) + 155 * Math.exp(-age[x][y] / 800f)),
								(int) (105 + 150 * (Math.exp(-age[x][y] / 1500f))))));
				drawHex(x - (int) (1d / 3 * lattice.length), y, g, r);
				// drawHex(x, y, g, r);
			}
		}
		g.dispose();
		try {
			String testName = "crystals/" + this.testName;
			ImageIO.write(bi, "jpg", new File(testName + "/gallery/" + name + ".jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void makeDirs() {
		File f = new File("crystals");
		if (!f.exists())
			f.mkdir();
		String testName = "crystals/" + this.testName;
		f = new File(testName);
		if (!f.exists())
			f.mkdir();
		f = new File(testName + "/data");
		if (!f.exists())
			f.mkdir();
		f = new File(testName + "/gallery");
		if (!f.exists())
			f.mkdir();
	}

	private void storeArray(double[][] board, String s) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < board.length; i++)
			for (int j = 0; j < board[0].length; j++) {
				builder.append(board[i][j] + "");
				if (j < board.length - 1)
					builder.append(",");
			}
		builder.append("\n");
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(s + ".txt"));
			writer.write(builder.toString());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void storeArray(int[][] board, String s) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < board.length; i++)
			for (int j = 0; j < board[0].length; j++) {
				builder.append(board[i][j] + "");
				if (j < board.length - 1)
					builder.append(",");
			}
		builder.append("\n");
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(s + ".txt"));
			writer.write(builder.toString());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void storeInfo(String s) {
		StringJoiner sj = new StringJoiner("\n");
		sj.add("deltaFar: " + deltaFar);
		sj.add("alphaFacet: " + alphaFacet);
		sj.add("alphaKink: " + alphaKink);
		sj.add("deltaX: " + deltaX);
		sj.add("X0: " + X0);
		sj.add("tbMin: " + 0.00025);
		sj.add("maxSeedError: " + maxSeedError);
		sj.add("maxError: " + maxError);
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(s + ".txt"));
			writer.write(sj.toString());
			writer.close();
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
