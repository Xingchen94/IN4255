package workshop;

import jv.object.PsDebug;
import jv.vecmath.PdVector;
import jv.vecmath.PiVector;
import jvx.numeric.PnBiconjugateGradient;
import jvx.numeric.PnSparseMatrix;

import java.util.ArrayList;

public class SurfaceSmoothing extends ShapeDeformation {

	/** Constructor */
	public SurfaceSmoothing() {
		super("Surface Smoothing");
		if (getClass() == SurfaceSmoothing.class) {
			init();
		}
	}

	public void iterative(double stepsize) {
		int numVertices = m_geom.getNumVertices();
		PiVector[] triangles = m_geom.getElements();
		ArrayList<Integer>[] vertex_neighbours = new ArrayList[numVertices];
		//Iterate over all triangles, building a list of neighbours per vertex
		for(int i = 0; i < triangles.length; i++){
			PiVector triangle = triangles[i];
			int[] vertices = triangle.getEntries();
			//Initialise ArrayList if necessary
			if (vertex_neighbours[vertices[0]] == null){
				vertex_neighbours[vertices[0]] = new ArrayList<Integer>();
			}
			if (vertex_neighbours[vertices[1]] == null){
				vertex_neighbours[vertices[1]] = new ArrayList<Integer>();
			}
			if (vertex_neighbours[vertices[2]] == null){
				vertex_neighbours[vertices[2]] = new ArrayList<Integer>();
			}
			
			//Add neighbours of 0 to neighbour list if they're new neighbours
			if (!vertex_neighbours[vertices[0]].contains(vertices[1])){
				vertex_neighbours[vertices[0]].add(vertices[1]);
			}
			if (!vertex_neighbours[vertices[0]].contains(vertices[2])){
				vertex_neighbours[vertices[0]].add(vertices[2]);
			}
			//Add neighbours of 1 to neighbour list if they're new neighbours
			if (!vertex_neighbours[vertices[1]].contains(vertices[0])){
				vertex_neighbours[vertices[1]].add(vertices[0]);
			}
			if (!vertex_neighbours[vertices[1]].contains(vertices[2])){
				vertex_neighbours[vertices[1]].add(vertices[2]);
			}
			//Add neighbours of 1 to neighbour list if they're new neighbours
			if (!vertex_neighbours[vertices[2]].contains(vertices[1])){
				vertex_neighbours[vertices[2]].add(vertices[1]);
			}
			if (!vertex_neighbours[vertices[2]].contains(vertices[0])){
				vertex_neighbours[vertices[2]].add(vertices[0]);
			}
		}
		//Calculate new vertex position for each vertex
		for(int i = 0; i < numVertices; i++){
			PdVector vertex = m_geom.getVertex(i);
			PdVector average = new PdVector(0, 0, 0);
			int numNeighbours = vertex_neighbours[i].size();
			for (int nb_id : vertex_neighbours[i]){
				PdVector nb_vertex = m_geom.getVertex(nb_id);
				average.add(nb_vertex);
			}
			average.multScalar(1/(float)numNeighbours);
			PdVector diff = new PdVector();
			diff.sub(average, vertex);
			diff.multScalar(stepsize);
			vertex.add(diff);
			m_geom.setVertex(i, vertex);
		}
		m_geom.update(m_geom);
	}

	public void explicit(double tau) {
		PsDebug.message("calculating explicit MCF");
		PnSparseMatrix matrixLaplacian = getLaplacian();

		// Get the current x/y/z values
		PdVector x = new PdVector(m_geom.getNumVertices());
		PdVector y = new PdVector(m_geom.getNumVertices());
		PdVector z = new PdVector(m_geom.getNumVertices());

		for(int i = 0; i < m_geom.getNumVertices(); i++) {
			x.setEntry(i, m_geom.getVertex(i).getEntry(0));
			y.setEntry(i, m_geom.getVertex(i).getEntry(1));
			z.setEntry(i, m_geom.getVertex(i).getEntry(2));
		}

		PsDebug.message("Calculating new x,y,z");
		// x = x - tLx, y = y - tLy, z = z - tLz
		PdVector Lx = PnSparseMatrix.rightMultVector(matrixLaplacian, x, null);
		PdVector Ly = PnSparseMatrix.rightMultVector(matrixLaplacian, y, null);
		PdVector Lz = PnSparseMatrix.rightMultVector(matrixLaplacian, z, null);

		Lx.multScalar(tau);
		x.sub(Lx);

		Ly.multScalar(tau);
		y.sub(Ly);

		Lz.multScalar(tau);
		z.sub(Lz);

		PsDebug.message("Setting new vertex coordinates");
		for (int vIndex = 0; vIndex < m_geom.getNumVertices(); vIndex++) {
			PdVector newV = new PdVector(3);
			newV.setEntry(0, x.getEntry(vIndex));
			newV.setEntry(1, y.getEntry(vIndex));
			newV.setEntry(2, z.getEntry(vIndex));

			m_geom.setVertex(vIndex, newV);
		}

		m_geom.update(m_geom);
	}

	public void implicit(double tau) {
		PsDebug.message("calculating implicit MCF");
		PsDebug.message("Calculating S matrix");
		PnSparseMatrix matrixG = meshToGradient();
		PnSparseMatrix MatrixGTranspose = PnSparseMatrix.transposeNew(matrixG);
		PnSparseMatrix matrixMv = getMv();
		PnSparseMatrix matrixM = getM();

		// Gt * Mv
		PnSparseMatrix GTMv = PnSparseMatrix.multMatrices(MatrixGTranspose, matrixMv, null);
		// Gt * Mv * G = S
		PnSparseMatrix MtS = PnSparseMatrix.multMatrices(GTMv, matrixG, null);

		MtS.multScalar(tau);
		// (M + tS)
		MtS.add(matrixM);

		// Get the current x/y/z values
		PdVector x = new PdVector(m_geom.getNumVertices());
		PdVector y = new PdVector(m_geom.getNumVertices());
		PdVector z = new PdVector(m_geom.getNumVertices());

		for(int i = 0; i < m_geom.getNumVertices(); i++) {
			x.setEntry(i, m_geom.getVertex(i).getEntry(0));
			y.setEntry(i, m_geom.getVertex(i).getEntry(1));
			z.setEntry(i, m_geom.getVertex(i).getEntry(2));
		}

		PsDebug.message("Calculating new x,y,z");
		PdVector Mx = PnSparseMatrix.rightMultVector(matrixM, x, null);
		PdVector My = PnSparseMatrix.rightMultVector(matrixM, y, null);
		PdVector Mz = PnSparseMatrix.rightMultVector(matrixM, z, null);

		PsDebug.message("Solving linear problems");
		try {
			// solve (M + tS)x~ = Mx for unknown x~
			PnBiconjugateGradient solver = new PnBiconjugateGradient();

			solver.solve(MtS, x, Mx);
			solver.solve(MtS, y, My);
			solver.solve(MtS, z, Mz);
		} catch (Exception e) {
			e.printStackTrace();
			PsDebug.message("Failed to solve.\n" + e.toString());
		}

		PsDebug.message("Linear problems solved");

		PsDebug.message("Updating mesh");
		for (int vIndex = 0; vIndex < m_geom.getNumVertices(); vIndex++) {
			PdVector newV = new PdVector(3);
			newV.setEntry(0, x.getEntry(vIndex));
			newV.setEntry(1, y.getEntry(vIndex));
			newV.setEntry(2, z.getEntry(vIndex));

			m_geom.setVertex(vIndex, newV);
		}

		m_geom.update(m_geom);
		PsDebug.message("Updated mesh");
	}

	private PnSparseMatrix getM() {
		PnSparseMatrix M = new PnSparseMatrix(m_geom.getNumVertices(), m_geom.getNumVertices(), 1);
		PiVector[] triangles = m_geom.getElements();

		for (int triangleIdx = 0; triangleIdx < triangles.length; triangleIdx++) {
			PiVector triangle = triangles[triangleIdx];
			double area = calcArea(triangle) / 3d;
			int[] vertices = m_geom.getElement(triangleIdx).m_data;
			M.setEntry(vertices[0], vertices[0], M.getEntry(vertices[0], vertices[0]) + area);
			M.setEntry(vertices[1], vertices[1], M.getEntry(vertices[1], vertices[1]) + area);
			M.setEntry(vertices[2], vertices[2], M.getEntry(vertices[2], vertices[2]) + area);
		}

		double max = Double.MIN_VALUE;
		double min = Double.MAX_VALUE;
		for(int i = 0; i < m_geom.getNumVertices();i++) {
			double value = M.getEntry(i,i);
			if(max < value) {
				max = value;
			} else if(min > value) {
				min = value;
			}
		}

		// Small improvement for meshes which are between -1 and 1 in 3d space
		// Mass matrix influences the pull of the stiffness matrix
		// So if the mass matrix is smaller than 1 it will shrink it to infinity when doing implicit euler
		// And it will explode with explicit euler
		if (max < 0.5d) {
			M.multScalar(1d / (max + min));
		}

		return M;
	}

	private PnSparseMatrix getLaplacian() {
		PsDebug.message("calculating laplacian matrix");

		PsDebug.message("Calculating S matrix");
		PnSparseMatrix matrixG = meshToGradient();
		PnSparseMatrix MatrixGTranspose = PnSparseMatrix.transposeNew(matrixG);
		PnSparseMatrix matrixMv = getMv();

		PsDebug.message("Calculating inverse M");
		PnSparseMatrix matrixM = getM();
		PnSparseMatrix matrixMInverse = new PnSparseMatrix(matrixM.getNumRows(), matrixM.getNumRows(),1);
		for(int i = 0; i < matrixM.getNumRows();i++) {
			matrixMInverse.setEntry(i,i, 1.0 / matrixM.getEntry(i,i));
		}

		// L = (M^-1) * S = (M^-1) * Gt * Mv * G
		return PnSparseMatrix.multMatrices(matrixMInverse,
				PnSparseMatrix.multMatrices(
					MatrixGTranspose,
					PnSparseMatrix.multMatrices(
							matrixMv,
							matrixG, null), null), null);
	}
}
