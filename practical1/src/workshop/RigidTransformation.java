package workshop;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import jv.geom.PgElementSet;
import jv.vecmath.PdMatrix;
import jv.vecmath.PdVector;
import jvx.project.PjWorkshop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *  Workshop for surface registration
 */

public class RigidTransformation extends PjWorkshop {
	
	/** First surface to be registered. */	
	PgElementSet	m_surfP;
    /** Keeps the a copy of the vertices of P when loaded
     * This can be used to reset P.
     */
    PdVector[] m_surfP_original;
	/** Second surface to be registered. */
	PgElementSet	m_surfQ;
    /** Keeps the a copy of the vertices of Q when loaded
     * This can be used to reset Q.
     */
    PdVector[] m_surfQ_original;

	/** Constructor */
	public RigidTransformation() {
		super("Surface Registration");
		if (getClass() == RigidTransformation.class) {
			init();
		}
	}
	
	/** Initialization */
	public void init() {
		super.init();
	}
	
	
	/** Set two Geometries. */
	public void setGeometries(PgElementSet surfP, PgElementSet surfQ) {
		m_surfP = surfP;
        m_surfP_original = PdVector.copyNew(surfP.getVertices());
		m_surfQ = surfQ;
        m_surfQ_original = PdVector.copyNew(surfQ.getVertices());
        System.out.println(String.format("Vertices P:%d vertices Q: %d", surfP.getVertices().length, surfQ.getVertices().length));
	}

    /**
     * Resets the two meshes to their initial state when they were loaded in.
     * No need to call update
     */
	public void reset() {
	    m_surfP.setVertices(m_surfP_original.clone());
        m_surfP.update(m_surfP);

        m_surfQ.setVertices(m_surfQ_original.clone());
        m_surfQ.update(m_surfQ);
    }

    /**
     * @param nrVertices The number of vertices to select
     * @return Selects a sublist of the vertices in p randomized.
     */
	public PdVector[] getRandomVertices(int nrVertices) {
		PdVector[] allVertices = m_surfP.getVertices();
		if (allVertices.length < nrVertices) {
		    return allVertices;
        }
        // Due references, we have to clone first
        List<PdVector> listVertices = Arrays.asList(allVertices.clone());
        // Use the java8 shuffle
        Collections.shuffle(listVertices);
        // Get the sublist
        return listVertices.subList(0, nrVertices).toArray(new PdVector[nrVertices]);
	}

    /**
     * For every entry in the given vertices finds the closest vertex in set Q for it.
     */
    public PdVector[] getClosestVertices(PdVector[] vertices) {
        PdVector[] closestVertices = new PdVector[vertices.length];
        for(int i = 0; i < vertices.length; i++) {
            closestVertices[i] = findClosestVertices(vertices[i]);
        }
        return closestVertices;
    }

    /**
     * Finds the closest vertex in the set Q from the given vertex.
     */
    private PdVector findClosestVertices(PdVector vertex) {
        // Note: this the most expensive function O(n^2)
        PdVector[] vertices = m_surfQ.getVertices();
        PdVector current = vertices[0];
        double smallest = vertex.dist(current);
        for(int i = 1; i < vertices.length -1; i++) {
            double dist = vertex.dist(vertices[i]);
            if(dist < smallest) {
                current = vertices[i];
                smallest = dist;
            }
        }
        return current;
    }

    /**
     * @param allDistances The distances
     * @return The median of the distances
     */
    public double getMedian(double[] allDistances) {
        double[] clonedDistances = allDistances.clone();
        Arrays.sort(clonedDistances);
        int middle = clonedDistances.length / 2;
        if (clonedDistances.length % 2 == 0) {
            return (clonedDistances[middle] + clonedDistances[middle - 1]) / 2.0;
        }
        return clonedDistances[middle];
    }

    /**
     * Calculates all the distances between left and right
     * @param left One set of vertices
     * @param right The other set
     * @return The list of distances between each vertices from each each set
     */
    public double[] getAllDistances(PdVector[] left, PdVector[] right) {
        double[] distances = new double[left.length];
        for(int i = 0; i < left.length; i++) {
            distances[i] = left[i].dist(right[i]);
        }
        return distances;
    }

    /**
     * Specifies which entries should be removed by setting the threshold: median * k
     * @param distances The list of distances
     * @param median The median
     * @param k
     * @return a list of size distances and every entry specifies if it should be removed or not
     */
    public boolean[] getRemoveList(double[] distances, double median, double k) {
        double threshold = median * k;
        boolean[] removeList = new boolean[distances.length];
        for(int i = 0; i < distances.length; i++) {
            removeList[i] = threshold < distances[i];
        }
        return removeList;
    }

    /**
     * Removes all the vertices in the given list specified by the listToRemove
     * @param vertices The list of vertices to be reduced
     * @param listToRemove The list that specifies which vertices to be removed
     * @return A sublist of vertices
     */
    public PdVector[] removeVertices(PdVector[] vertices, boolean[] listToRemove) {
        List<PdVector> reducedList = new ArrayList<>();
        for(int i = 0; i < vertices.length; i++) {
            if(!listToRemove[i]) {
                reducedList.add(vertices[i]);
            }
        }
        return reducedList.toArray(new PdVector[reducedList.size()]);
    }

    /**
     * Computes the
     * @param verticesP
     * @param verticesQ
     * @return
     */
    public PdMatrix computeM(PdVector[] verticesP, PdVector[] verticesQ) {
        PdVector centroidP = computeCentroid(verticesP);
        PdVector centroidQ = computeCentroid(verticesQ);

        PdMatrix M = new PdMatrix(3);
        for(int i = 0; i < verticesP.length; i++) {
            PdMatrix mRow = new PdMatrix();
            PdVector pCentroidP = PdVector.subNew(verticesP[i], centroidP);
            PdVector qCentroidQ = PdVector.subNew(verticesQ[i], centroidQ);
            // adjoin == p * q^t
            mRow.adjoint(pCentroidP, qCentroidQ);
            M.add(mRow);
        }
        M.multScalar(1.0 / (double)verticesP.length);
        return M;
    }

    /**
     * @param vertices
     * @return The central point in the vertices
     */
    private PdVector computeCentroid(PdVector[] vertices) {
        PdVector centroid = new PdVector();
        for(int i = 0; i < vertices.length; i++) {
            centroid.add(vertices[i]);
        }
        centroid.multScalar(1.0 / (double)vertices.length);
        return centroid;
    }

    public PdMatrix computeOptimalRotation(SingularValueDecomposition svd) {
        Matrix identity = Matrix.identity(3,3);

        // Using Jama here instead of PdMatrix, because Jama's api is better
        Matrix V = svd.getV();
        Matrix Ut = svd.getU().transpose();
        Matrix VUt = V.times(Ut);

        identity.set(2, 2, VUt.det());
        Matrix rotation = V.times(identity).times(Ut);

        return new PdMatrix(rotation.getArrayCopy());
    }

    public PdVector computeOptimalTranslation(PdVector[] pointsP, PdVector[] pointsQ, PdMatrix optimalRotation) {
        PdVector centroidP = computeCentroid(pointsP);
        PdVector centroidQ = computeCentroid(pointsQ);
        return PdVector.subNew(centroidQ, optimalRotation.leftMultMatrix(null, centroidP));
    }

    public void rotateMesh(PdMatrix rotation, PgElementSet mesh) {
        PdVector[] vertices = mesh.getVertices();
        for(int i = 0; i < vertices.length; i++) {
            vertices[i].leftMultMatrix(rotation);
        }
    }

    public void translateMesh(PdVector translation, PgElementSet mesh) {
        PdVector[] vertices = mesh.getVertices();
        for(int i = 0; i < vertices.length; i++) {
            vertices[i].add(translation);
        }
    }

    /**
     * Calculates the MSE of the distances between left and right vertices set.
     * @param left
     * @param right
     * @return the MSE
     */
    public double calculateError(PdVector[] left, PdVector[] right) {
        double error = 0;
        for(int i = 0; i < left.length; i++) {
            double distance = left[i].dist(right[i]);
            error += (distance * distance);
        }
        return error / (double)left.length;
    }

    public void randomRotationQ() {
        // A rotation of 2 degrees around the axis (1, 1, 1)
        // If you click enough it would be random
        PdMatrix rotation = new PdMatrix(new double[][]{
                {0.8959026, 0.3618392, -0.2577418},
                {-0.2577418, 0.8959026, 0.3618392},
                {0.3618392, -0.2577418, 0.8959026}
        });
        rotateMesh(rotation, this.m_surfQ);
    }

    /**
     * Random translation in x,y,z between - size / 2 and size / 2
     * @param size The boundaries
     */
    public void randomTranslationQ(int size) {
        PdVector translation = new PdVector(Math.random() * size - size / 2, Math.random() * size - size / 2, Math.random() * size - size / 2);
        translateMesh(translation, this.m_surfQ);
    }
}
