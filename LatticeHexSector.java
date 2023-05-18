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

public class LatticeHexSector {
	private int[][] lattice;
	private int[][] age;
	private double[][] growth;
	private double[][] delta;

	private double deltaFar = 0.5;
	private double alphaFacet = 1;
	private double A;

	private double alphaKink = 1;
	private double deltaX = 0.0002;
	private double X0 = 0.0004;
	private double tbMin = 0.00025;

	private double crystalRadius;
	private double satRadiusApprox;
	private int size;

	private double maxSeedError = 1E-4;
	private double maxError = 1E-4;

	private String testName;
	public int thread;

	private int overtime = 120;

	private volatile static int t = 0, next = 0;

	public static void main(String[] args) throws InterruptedException {

		/*
		 * double[] sat = { 2.5, 5, 7.5, 10, 15 }; double[] fac = { 0, 0.001, 0.01,
		 * 0.025, 0.05, 0.1 }; double[] A = { 10, 0.85 };
		 * 
		 * double[][] params = new double[sat.length * fac.length * A.length][3];
		 * 
		 * int i = 0; for (double sa : sat) for (double fa : fac) for (double a : A)
		 * params[i++] = new double[] { sa, fa, a };
		 */

		double[][] params = { { 10, 0, 0.75 }, { 10, 0, 0.5 }, { 10, 0, 1 }, { 10, 0, 2 }, { 10, 0, 3 }, { 10, 0, 4 },
				{ 10, 0, 5 }, { 10, 0, 10 }, { 10, 0.025, 4 }, { 10, 0, 0.25 }, { 10, 0.1, 0.75 }, { 10, 0.1, 1 },
				{ 10, 0.1, 2 }, { 10, 0.1, 3 }, { 10, 0.1, 4 }, { 10, 0.1, 5 }, { 15, 0, 2 }, { 15, 0, 3 },
				{ 2.5, 0, 0.75 }, { 2.5, 0, 1 }, { 2.5, 0, 2 }, { 2.5, 0, 3 }, { 2.5, 0.05, 1 }, { 5, 0, 5 },
				{ 5, 0.001, 0.75 }, { 5, 0.05, 1 }, { 5, 0.05, 2 }, { 5, 0.1, 1 }, { 7.5, 0, 0.75 }, { 7.5, 0, 3 },
				{ 7.5, 0, 5 }, { 7.5, 0.001, 0.75 }, { 7.5, 0.025, 0.75 }, { 5, 0.1, 0.75 }, { 7.5, 0, 2 },
				{ 7.5, 0.001, 2 }, { 7.5, 0.01, 2 }, { 7.5, 0.02, 0.25 }, { 7.5, 0.05, 2 }, { 7.5, 0.001, 1 },
				{ 30, 0.05, 0.75 }, { 7.5, 0.025, 0 }, { 7.5, 0.025, 20 } };
		System.out.println("Tests: " + params.length);

		int s = 1200;
		int i = 0;

		while (true) {
			if (t < 6 && params.length > next) {
				double dfar = params[next][0];
				double af = params[next][1];
				double a = params[next][2];
				if (dfar <= 0.1 && af <= 0.025) {
					// System.out.println("Aborted");
					// next++;
					// continue;
				}
				new Thread(new Runnable() {
					@Override
					public void run() {
						double[] param = params[next++];
						LatticeHexSector lh = new LatticeHexSector(s);
						lh.setX0(0.0005);// 0.001 = detailed; 0.00032 = balanced; 0.0001 = overall picture
						lh.setTbMin(0.0001);// Don't change (0.0001 if lost)
						lh.setDeltaFar(param[0]);
						lh.setAlphaFacet(param[1]);
						lh.setA(param[2]);
						lh.thread = next;

						System.out.println("Started " + lh.thread);
						lh.seed();
						lh.runCapture((int) (s / 1.5), 100, false);
						System.out.println(lh.thread + " Finished");
						t--;
					}
				}).start();
				t++;
			}
			Thread.sleep(1000);
		}
	}

	public LatticeHexSector(int size, String testName) {
		size += 5;
		this.testName = testName;
		this.size = size;
		this.lattice = initInt(size);
		this.growth = initDouble(size);
		this.delta = initDouble(size);
		this.age = initInt(size);
	}

	public double[][] initDouble(int size) {
		double[][] m = new double[size / 2][];
		for (int i = size; i > 1; i -= 2)
			m[i / 2 - 1] = new double[i];
		return m;
	}

	public int[][] initInt(int size) {
		int[][] m = new int[size / 2][];
		for (int i = size; i > 1; i -= 2)
			m[i / 2 - 1] = new int[i];
		return m;
	}

	public LatticeHexSector(int size) {
		this(size, null);
	}

	private void runCapture(int iter, int cap, boolean b) {
		long l = System.currentTimeMillis();
		makeDirs();
		storeInfo("crystalsS/" + testName + "/data/info");
		save("Step 0");
		for (int i = 0; b ? i < iter / cap : iter > crystalRadius; i++) {
			System.out.println("Thread " + thread + " - Step " + (i + 1));
			if (!run(cap, b, l)) {
				System.out.println(thread + " Aborted");
				return;
			}
			save("Step " + (i + 1));
		}
	}

	private boolean run(int i, boolean b, long l) {
		double maxDist = this.crystalRadius;
		for (int x = 0; b ? x < i : this.crystalRadius < (maxDist + 1) * 1.1; x++) {
			while (updateDelta() > maxError)
				if (System.currentTimeMillis() - l > 1000 * overtime * 60)
					return false;
			updateGrowth();
			updateCells();
			if (System.currentTimeMillis() - l > 1000 * overtime * 60)
				return false;
		}
		return true;
	}

	private void seed() {
		this.testName = "size=" + this.size + ", dF=" + deltaFar + ", aF=" + alphaFacet + ", A=" + A;
		for (int x = 0; x < delta.length; x++)
			for (int y = 0; y < delta[x].length; y++)
				delta[x][y] = deltaFar;
		lattice[size / 2 - 2][size - 5] = 1;
		lattice[size / 2 - 2][size - 6] = 1;
		lattice[size / 2 - 3][size - 6] = 1;
		updateCells();
		while (updateDelta() > maxSeedError) {
		}
	}

	private void updateGrowth() {
		int[][] latt = clone(this.lattice);
		double del = tbMin / deltaX;
		double tolerance = 0.995;
		this.satRadiusApprox = 0;
		for (int x = 1; x < lattice.length - 1; x++)
			for (int y = 1; y < lattice[x].length - 2; y++) {
				int ice = count(x, y, 1);
				if (lattice[x][y] == 2) {
					if (lattice[lattice.length - 1].length - y > crystalRadius)
						crystalRadius = lattice[lattice.length - 1].length - y;
					growth[x][y] += (ice == 1 ? 0
							: ice == 2 ? alpha(alphaFacet, delta[x][y])
									: ice == 3 || ice == 4 ? alphaKink : ice == 5 ? 2 : 100000)
							* del * delta[x][y];
					if (growth[x][y] >= 1)
						latt[x][y] = 1;
				} else if (lattice[x][y] == 0)
					if (Math.abs(delta[x][y] / deltaFar - 1) <= 1 - tolerance && satRadiusApprox < y)
						satRadiusApprox = y;
			}
		lattice = latt;
	}

	private double alpha(double alpha, double delta) {
		return A * Math.exp(-alphaFacet / delta);
	}

	private void updateCells() {
		// Reflect boundaries
		for (int y = 1; y <= lattice[lattice.length - 4].length; y++)
			lattice[lattice.length - 1][y] = lattice[lattice.length - 3][y - 1];
		for (int x = 0; x < lattice.length - 3; x++) {
			lattice[x][2 * x + 1] = lattice[x + 1][2 * x + 1];
			lattice[x][2 * x + 2] = lattice[x + 2][2 * x + 2];
		}
		int[][] latt = clone(this.lattice);
		for (int x = 1; x < lattice.length - 1; x++)
			for (int y = 1; y < lattice[x].length - 2; y++) {
				int ice = count(x, y, 1);
				if (lattice[x][y] == 0 && ice > 0)
					latt[x][y] = 2;
				if (lattice[x][y] == 1)
					age[x][y]++;
				if (lattice[x][y] == 2 && ice > 5)
					latt[x][y] = 1;
			}
		lattice = latt;
		for (int y = 1; y <= lattice[lattice.length - 4].length; y++)
			lattice[lattice.length - 1][y] = lattice[lattice.length - 3][y - 1];
		for (int x = 0; x < lattice.length - 3; x++) {
			lattice[x][2 * x + 1] = lattice[x + 1][2 * x + 1];
			lattice[x][2 * x + 2] = lattice[x + 2][2 * x + 2];
		}
	}

	private double updateDelta() {
		for (int x = 0; x < lattice.length; x++)
			delta[x][0] = deltaFar;
		for (int y = 1; y <= lattice[lattice.length - 4].length; y++)
			delta[lattice.length - 1][y] = delta[lattice.length - 3][y - 1];
		for (int x = 0; x < lattice.length - 3; x++) {
			delta[x][2 * x + 1] = delta[x + 1][2 * x + 1];
			delta[x][2 * x + 2] = delta[x + 2][2 * x + 2];
		}
		double[][] delta0 = clone(this.delta);
		double del = deltaX / X0;
		double maxDelta = 0;
		for (int x = 1; x < lattice.length - 1; x++)
			for (int y = 1; y < lattice[x].length - 2; y++) {
				if (y < satRadiusApprox - 10)
					continue;
				if (lattice[x][y] == 0) {
					int vapor = count(x, y, 0), bound = count(x, y, 2);
					delta0[x][y] = (delta[x][y - 1] + delta[x][y + 1] + delta[x - 1][y] + delta[x + 1][y]
							+ delta[x - 1][y - 1] + delta[x + 1][y + 1]) / (bound + vapor);
				} else if (lattice[x][y] == 2) {
					int ice = count(x, y, 1);
					int opp = (lattice[x][y - 1] == 0 && lattice[x][y + 1] == 1 ? 1 : 0)
							+ (lattice[x][y + 1] == 0 && lattice[x][y - 1] == 1 ? 1 : 0)
							+ (lattice[x - 1][y] == 0 && lattice[x + 1][y] == 1 ? 1 : 0)
							+ (lattice[x + 1][y] == 0 && lattice[x - 1][y] == 1 ? 1 : 0)
							+ (lattice[x + 1][y + 1] == 0 && lattice[x - 1][y - 1] == 1 ? 1 : 0)
							+ (lattice[x - 1][y - 1] == 0 && lattice[x + 1][y + 1] == 1 ? 1 : 0);
					if (opp == 0)
						continue;

					double d = ((lattice[x][y - 1] == 0 && lattice[x][y + 1] == 1 ? delta[x][y - 1] : 0)
							+ (lattice[x][y + 1] == 0 && lattice[x][y - 1] == 1 ? delta[x][y + 1] : 0)
							+ (lattice[x - 1][y] == 0 && lattice[x + 1][y] == 1 ? delta[x - 1][y] : 0)
							+ (lattice[x + 1][y] == 0 && lattice[x - 1][y] == 1 ? delta[x + 1][y] : 0)
							+ (lattice[x - 1][y - 1] == 0 && lattice[x + 1][y + 1] == 1 ? delta[x - 1][y - 1] : 0)
							+ (lattice[x + 1][y + 1] == 0 && lattice[x - 1][y - 1] == 1 ? delta[x + 1][y + 1] : 0))
							/ opp;
					if (ice == 2)
						delta0[x][y] = d / (1 + alpha(alphaFacet, delta[x][y]) * del);
					else if (ice == 3 || ice == 4)
						delta0[x][y] = d / (1 + alphaKink * del);
					else if (ice == 1)
						delta0[x][y] = d;
					else if (ice == 5)
						delta0[x][y] = d / (1 + 2 * del);
					else
						delta[x][y] = 0;
				}
				double c = Math.abs(delta[x][y] - delta0[x][y]) / delta0[x][y];
				if (c > maxDelta)
					maxDelta = c;
			}
		this.delta = delta0;
		return maxDelta;

	}

	public int count(int x, int y, int n) {
		int q = 0;
		q += lattice[x][y - 1] == n ? 1 : 0;
		q += lattice[x][y + 1] == n ? 1 : 0;
		q += lattice[x - 1][y] == n ? 1 : 0;
		q += lattice[x + 1][y] == n ? 1 : 0;

		q += lattice[x - 1][y - 1] == n ? 1 : 0;
		q += lattice[x + 1][y + 1] == n ? 1 : 0;
		return q;
	}

	// Utility
	private double[][] clone(double[][] delta) {
		double[][] n = initDouble(this.size);
		for (int x = 0; x < delta.length; x++)
			for (int y = 0; y < delta[x].length; y++)
				n[x][y] = delta[x][y];
		return n;
	}

	private int[][] clone(int[][] lattice) {
		int[][] n = initInt(this.size);
		for (int x = 0; x < lattice.length; x++)
			for (int y = 0; y < lattice[x].length; y++)
				n[x][y] = lattice[x][y];
		return n;
	}

	public void save1(String name) {
		int r = 5;
		BufferedImage bi = new BufferedImage((int) (lattice.length * r * 4f), (int) (lattice.length * r * 4f),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) bi.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		for (int x = 1; x < lattice.length - 1; x++) {
			for (int y = 1; y < lattice[x].length - 2; y++) {
				double t = age[x][y] * X0 / 0.0005;
				int c = (int) (Math.min(255 * (1 - Math.exp(-delta[x][y] / 10)), 255));
				g.setColor(lattice[x][y] == 0 || lattice[x][y] == 2 ? new Color(c, c, c)
						: new Color((int) (55 * (Math.exp(-t / 15000f)) + 200 * (Math.exp(-t / 200f))),
								(int) (80 * (Math.exp(-t / 15000f)) + 175 * Math.exp(-t / 800f)),
								(int) (80 + 175 * (Math.exp(-t / 1500f)))));
				drawHex(x, lattice.length + y - x, g, r);
				// drawHex(x, y, g, r);
			}
		}
		g.dispose();
		try {
			String testName = "crystalsS/" + this.testName;
			ImageIO.write(resize(bi, 4000, 4000), "jpg", new File(testName + "/gallery/" + name + ".jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void drawHex(int x, int y, Graphics2D g, int r) {
		double x0 = lattice.length - 2;
		double y0 = lattice[lattice.length - 1].length - 3;
		double x00 = lattice.length * 2 * r - (r * (x0 + 0.5 * y0) - (r + 1) * 0.5);
		double y00 = r * y0 * Math.sqrt(3) / 2 - (r + 1) / 2;

		double px = lattice.length * 2 * r - (r * (x + 0.5 * y) - (r + 1) * 0.5) - x00;
		double py = r * y * Math.sqrt(3) / 2 - (r + 1) / 2 - y00;

		x00 = lattice.length * r * 2f;
		y00 = lattice.length * r * 2f;

		r += 4;

		for (int i = 0; i < 6; i++) {
			double theta = Math.atan(py / px);
			double r0 = Math.sqrt(px * px + py * py);

			g.fillOval((int) (r0 * Math.cos(Math.PI / 3 * i + theta) + x00),
					(int) (r0 * Math.sin(Math.PI / 3 * i + theta) + y00), r, r);
			double px0 = (-px + -Math.sqrt(3) * py) / 2;
			double py0 = (-Math.sqrt(3) * px + py) / 2;

			theta = Math.atan(py0 / px0);
			r0 = Math.sqrt(px * px + py * py);

			g.fillOval((int) (r0 * Math.cos(Math.PI / 3 * i + theta) + x00),
					(int) (r0 * Math.sin(Math.PI / 3 * i + theta) + y00), r, r);
		}
	}

	public void save(String name) {
		save1("a " + name);
		save3(name);
	}

	private void save3(String name) {
		String testName = "crystalsS/" + this.testName;
		storeArray(delta, testName + "/data/" + name + "_delta");
		storeArray(lattice, testName + "/data/" + name + "_lattice");
		storeArray(growth, testName + "/data/" + name + "_growth");
		storeArray(age, testName + "/data/" + name + "_age");
	}

	private void makeDirs() {
		File f = new File("crystalsS");
		if (!f.exists())
			f.mkdir();
		String testName = "crystalsS/" + this.testName;
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
			for (int j = 0; j < board[i].length; j++) {
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
			for (int j = 0; j < board[i].length; j++) {
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

	public double getA() {
		return A;
	}

	public void setA(double a) {
		A = a;
	}
}
