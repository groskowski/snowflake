import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class Rotating {
	int[][] lattice;
	double[][] fb;
	int[][] age;
	double[][] delta;

	public Rotating(int w, int h) {
		lattice = new int[w][h];
		age = new int[w][h];
		delta = new double[w][h];
		fb = new double[w][h];
		for (int x = 1; x < lattice.length - 1; x++)
			for (int y = 1; y < lattice[0].length - 1; y++)
				delta[x][y] = alpha;
	}

	private void initiate(int size) {
		// int y = 0;// hex

		/*
		 * for (int i = size; i <= 2 * size; i++) { for (int x = lattice.length / 2 - i
		 * / 2; x <= lattice.length / 2 + i / 2; x++) { lattice[lattice[0].length / 2 -
		 * size + y / 2][x] = 1; lattice[lattice[0].length / 2 + size - y / 2][x] = 1; }
		 * y++; } ;
		 */

		for (int i = 0; i < 2 * size; i++) {
			for (int x = lattice.length / 2 - i / 2; x <= lattice.length / 2 + i / 2; x++) {
				lattice[x][lattice[0].length / 2 + i / 2 - size] = 1;
				lattice[x][lattice[0].length / 2 + 2 * size - i / 2 - 2 - size] = 1;
				delta[x][lattice[0].length / 2 + i / 2 - size] = 0;
				delta[x][lattice[0].length / 2 + 2 * size - i / 2 - 2 - size] = 0;
			}
		}

		/*
		 * for (int x = lattice.length / 2 - size / 2; x <= lattice.length / 2 + size /
		 * 2; x++) { for (int y = lattice.length / 2 - size / 2; y <= lattice.length / 2
		 * + size / 2; y++) { lattice[x][y] = 1; delta[x][y] = 0; } }
		 */
		/*
		 * for (int i = 0; i < size; i++) { for (int e = 0; e < 360; e++) { int x =
		 * (int) (i / 2 * Math.cos(Math.toRadians(e))), y = (int) (i / 2 *
		 * Math.sin(Math.toRadians(e))); lattice[x + lattice.length / 2][y +
		 * lattice[0].length/2] = 1; } }
		 */
		/*
		 * for (int e = 0; e < 360; e++) { int x = (int) (size * (1 + e / 30d) / 2 *
		 * Math.cos(Math.toRadians(e))), y = (int) (size * (1 + e / 30d) / 2 *
		 * Math.sin(Math.toRadians(e))); lattice[x + lattice.length / 2][y +
		 * lattice[0].length / 2] = 1; }
		 */
		/*
		 * for (int i = 0; i < 30; i++) for (int e = 0; e < 360; e++) { double r = i*0.7
		 * / Math.sin(Math.toRadians(e - 30 * (-1 + Math.floor(e / 30)))); int x = (int)
		 * (r * Math.cos(Math.toRadians(e))), y = (int) (r *
		 * Math.sin(Math.toRadians(e))); lattice[x + lattice.length / 2][y +
		 * lattice[0].length / 2] = 1; }
		 */
		/*
		 * for (int i = 0; i < 30; i++) for (int e = 0; e < 360; e++) { double r = i*0.7
		 * / Math.sin(Math.toRadians(e +90- 120 * (-1 + Math.floor(e / 120)))); int x =
		 * (int) (r * Math.cos(Math.toRadians(e))), y = (int) (r *
		 * Math.sin(Math.toRadians(e))); lattice[x + lattice.length / 2][y +
		 * lattice[0].length / 2] = 1; }
		 */
		/*
		 * for (int x = lattice.length / 2 - size / 2; x <= lattice.length / 2 + size /
		 * 2; x++) { lattice[x][lattice[0].length/2] = 1; }
		 */
		updateCells();
	}

	static int size = 300;

	public static void main(String[] args) throws InterruptedException, IOException {
		Rotating lat = new Rotating(size, size);

		int q = 1;
		lat.initiate(19);
		lat.save(q + "");
		for (int e = 0; e < 500; e++)
			lat.updateSupersat();

		DisplayImage di = null;

		for (int i = 0; i < 10000; i++) {
			if (i % 10 == 0)
				di = new DisplayImage(q + ".jpg");
			// alpha = Math.abs(Math.sin(time++/100f));
			// time++;
			for (int e = 0; e < 50; e++)
				lat.updateSupersat();
			lat.updateFB();
			lat.updateCells();
			if (i % 10 == 9) {
				lat.save((q + 1) + "");
				di.die();
			}
			q++;
		}
	}

	static double alpha = 0.5;
	private double deltaX = 0.0001;
	private double X0 = 0.0003;

	private static int time = 0;

	private void updateSupersat() {
		double[][] delta = clone(this.delta);
		for (int x = 0; x < lattice.length - 1; x++) {
			delta[x][0] = alpha;
			delta[x][delta[0].length - 1] = alpha;
		}
		for (int y = 0; y < lattice[0].length - 1; y++) {
			delta[0][y] = alpha;
			delta[delta.length - 1][y] = alpha;
		}
		for (int y = 1; y < lattice[0].length - 1; y++)
			for (int x = 1; x < lattice.length - 1; x++) {
				double x0 = x - lattice.length / 2d, y0 = y - lattice[0].length / 2d;
				double vx = y0 / Math.sqrt(x0 * x0 + y0 * y0) / 50;
				double vy = -x0 / Math.sqrt(x0 * x0 + y0 * y0) / 50;
				int ice = countIce(x, y), vapor = countVapor(x, y), bound = countBoundary(x, y);
				if (lattice[x][y] == 2) {
					delta[x][y] = 0;
					if (ice == 1) {// Facet
						double d = 0;
						if (lattice[x + 1][y] == 0)
							d += this.delta[x + 1][y];
						if (lattice[x - 1][y] == 0)
							d += this.delta[x - 1][y];
						if (lattice[x][y + 1] == 0)
							d += this.delta[x][y + 1];
						if (lattice[x][y - 1] == 0)
							d += this.delta[x][y - 1];

						delta[x][y] = d / (1 + alphaFacet * (deltaX) / X0) / vapor;
					} else if (ice == 2) {// Kink
						double d = 0;
						if (lattice[x + 1][y] == 0) {
							d += this.delta[x + 1][y];
						}
						if (lattice[x - 1][y] == 0) {
							d += this.delta[x - 1][y];
						}
						if (lattice[x][y + 1] == 0) {
							d += this.delta[x][y + 1];
						}
						if ((lattice[x][y - 1] == 0)) {
							d += this.delta[x][y - 1];
						}
						delta[x][y] = 0.5 * d / (1 + 1 * (deltaX) / X0 / Math.sqrt(2));
					}
				} else if (lattice[x][y] == 0 && vapor + bound > 0) {

					delta[x][y] = (this.delta[x + 1][y] * (1 - vx) + this.delta[x - 1][y] * (1 + vx)
							+ this.delta[x][y + 1] * (1 - vy) + this.delta[x][y - 1] * (1 + vy)) / (vapor + bound);
				}
			}
		this.delta = delta;
	}

	private int countBoundary(int x, int y) {
		return (lattice[x + 1][y] == 2 ? 1 : 0) + (lattice[x][y + 1] == 2 ? 1 : 0) + (lattice[x - 1][y] == 2 ? 1 : 0)
				+ (lattice[x][y - 1] == 2 ? 1 : 0);
	}

	private void updateFB() {
		int[][] latt = clone(this.lattice);
		double tbmin = 0.0002;
		for (int y = 1; y < lattice[0].length - 1; y++)
			for (int x = 1; x < lattice.length - 1; x++) {
				int ice = countIce(x, y), vapor = countVapor(x, y), bound = countBoundary(x, y);
				if (lattice[x][y] == 2) {
					if (ice == 1) {
						fb[x][y] += alphaFacet / deltaX * tbmin * delta[x][y];
					} else if (ice == 2) {
						fb[x][y] += 1 / deltaX * tbmin * delta[x][y] * Math.sqrt(2);
					}
					if (fb[x][y] >= 1) {
						fb[x][y] = 0;
						latt[x][y] = 1;
					}
				}
			}
		lattice = latt;
	}

	static double alphaFacet = 1;

	public void updateCells() {
		int[][] latt = clone(this.lattice);
		for (int y = 1; y < lattice[0].length - 1; y++)
			for (int x = 1; x < lattice.length - 1; x++) {
				int ice = countIce(x, y), vapor = countVapor(x, y);
				if (lattice[x][y] == 0) {
					if (ice > 0) {
						latt[x][y] = 2;
					}
				}
				if (lattice[x][y] == 2) {
					if (ice > 3)
						latt[x][y] = 1;
				}
				if (lattice[x][y] == 1)
					age[x][y]++;
			}
		for (int y = 0; y < lattice[0].length; y++)
			for (int x = 0; x < lattice.length; x++)
				lattice[x][y] = latt[x][y];
	}

	private int countVapor(int x, int y) {
		return (lattice[x + 1][y] == 0 ? 1 : 0) + (lattice[x][y + 1] == 0 ? 1 : 0) + (lattice[x - 1][y] == 0 ? 1 : 0)
				+ (lattice[x][y - 1] == 0 ? 1 : 0);
	}

	private int countIce(int x, int y) {
		return (lattice[x + 1][y] == 1 ? 1 : 0) + (lattice[x][y + 1] == 1 ? 1 : 0) + (lattice[x - 1][y] == 1 ? 1 : 0)
				+ (lattice[x][y - 1] == 1 ? 1 : 0);
	}

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
		BufferedImage bi = new BufferedImage(lattice.length, lattice[0].length, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < lattice[0].length; y++) {
			for (int x = 0; x < lattice.length; x++) {
				int c = (int) (Math.min(255 * delta[x][y], 255));
				bi.setRGB(x, y,
						lattice[x][y] == 0 ? new Color(c, c, c).getRGB()
								: lattice[x][y] == 1
										? (new Color((int) (255 * (Math.exp(-age[x][y] / 50f))),
												(int) (255 * Math.exp(-age[x][y] / 300f)),
												(int) (255 * (Math.exp(-age[x][y] / 1000f)))).getRGB())
										: Color.BLACK.getRGB());
			}
		}
		try {
			ImageIO.write(
					resize(bi, bi.getWidth() * (300 / lattice.length), bi.getHeight() * (300 / lattice[0].length)),
					"jpg", new File(name + ".jpg"));
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
}
