/*
 * Implementation of the Hilbert basis computation
 */
package editor.domain.semiflows;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 *
 * @author elvio
 */
public class HilbertBasis {  
    private ArrayList<Row> rows;
    private final int N, M;
    private boolean verbose = false;
    private boolean keepCpCm = false;
    
    //-----------------------------------------------------------------------
    // Empty constructor: Initialize [identity(N*N) | zero(N*M)]
    public HilbertBasis(int N, int M) {
        this.N = N;
        this.M = M;
        rows = new ArrayList<>();
        for (int n=0; n<N; n++) {
            Row row = new Row(N, M, true);
            row.e[n] = 1;
            rows.add(row);
        }
    }
    
    // Initialize from an N*M matrix
    public HilbertBasis(int[][] mat) {
        this(mat.length, mat[0].length);
        for (int i=0; i<mat.length; i++) {
            System.arraycopy(mat[i], 0, rows.get(i).l, 0, mat[i].length);
        }
    }

    //-----------------------------------------------------------------------
    public void setKeepCpCm() {
        keepCpCm = true;
    }
    public void setVerbose() {
        verbose = true;
    }
    public void addToL(int i, int j, int val) {
        rows.get(i).l[j] += val;
    }
    public int numRows() { 
        return rows.size(); 
    }
    public int[] getBasisVec(int i) {
        return rows.get(i).e;
    }
    public boolean isRealBasisVec(int i) {
        return rows.get(i).is_l_zero();
    }
    public void removeInitialRows() {
        rows.removeIf(row -> row.initial);
    }
    
    //-----------------------------------------------------------------------

    private boolean partialOrderCmp(Row row1, Row row2, int j) {
        return (row1.l[j] * row2.l[j] >= 0 && 
                Math.abs(row1.l[j]) <= Math.abs(row2.l[j]) &&
                row1.less_equal_e(row2));
    }
        
    //-----------------------------------------------------------------------
    // Generate all sums of R+ rows with R- rows into C
    private void S_vectors(ArrayList<Row> Rp, ArrayList<Row> Rm, ArrayList<Row> C) {
        for (Row rowp : Rp) {
            for (Row rowm : Rm) {
                Row rowSum = new Row(N, M, false);
                rowSum.add(rowp);
                rowSum.add(rowm);
                appendUnique(rowSum, C);
            }
        }
    }
    
    //-----------------------------------------------------------------------
    
    private boolean isIrreducible(Row r, int j, ArrayList<Row> C0,
                                  ArrayList<Row> Cp, ArrayList<Row> Cm) 
    {
        for (Row r2 : C0)
            if (partialOrderCmp(r2, r, j))
                return false;
        for (Row r2 : Cp)
            if (partialOrderCmp(r2, r, j))
                return false;
        for (Row r2 : Cm)
            if (partialOrderCmp(r2, r, j))
                return false;
        return true;
    }
    
    //-----------------------------------------------------------------------
    private static boolean appendUnique(Row row, ArrayList<Row> rows) {
        for (Row row2 : rows) {
            if (row2.equal(row))
                return false; // Already exists
        }
        rows.add(row);
        return true;
    }
    
    //-----------------------------------------------------------------------
    private static void appendAllUnique(ArrayList<Row> toAppend, ArrayList<Row> rows) {
        for (Row r : toAppend)
            appendUnique(r, rows);
    }

    //-----------------------------------------------------------------------
    private void HilbertFMcol(int j) {
        ArrayList<Row> C0 = new ArrayList<>(); // C0 = rows with c[j] == 0
        ArrayList<Row> Cp = new ArrayList<>(); // C+ = rows with c[j] > 0
        ArrayList<Row> Cm = new ArrayList<>(); // C- = rows with c[j] < 0

        ArrayList<Row> G = new ArrayList<>(rows);
        ArrayList<Row> supp = new ArrayList<>();
        // Pottier algorithm
        while (!G.isEmpty()) {
            G.sort(Row.DEGLEX_COMPARATOR);
            Row el = G.get(0);
            G.remove(0);
            if (isIrreducible(el, j, C0, Cp, Cm)) {
                if (el.l[j] == 0) {
                    appendUnique(el, C0);
                    if (verbose)
                        System.out.println("C0 <- C0 U "+el);
                }
                else if (el.l[j] > 0) {
                    appendUnique(el, Cp);
                    supp.add(el);
                    S_vectors(supp, Cm, G);
                    supp.clear();
                    if (verbose)
                        System.out.println("C+ <- C+ U "+el);
                }
                else if (el.l[j] < 0) {
                    appendUnique(el, Cm);
                    supp.add(el);
                    S_vectors(Cp, supp, G);
                    supp.clear();
                    if (verbose)
                        System.out.println("C- <- C- U "+el);
                }
            }
            else {
                if (verbose)
                    System.out.println("DROP "+el);
            }
        }
        if (keepCpCm) {
            appendAllUnique(C0, rows);
            appendAllUnique(Cp, rows);
            appendAllUnique(Cm, rows);
//            rows.addAll(Cp);
//            rows.addAll(Cm);
//            rows.addAll(C0);            
        }
        else {
            appendAllUnique(C0, rows);
//            rows.clear();
//            rows.addAll(C0);            
        }
    }
    
    //-----------------------------------------------------------------------
    // Heuristic that chooses the next pivot of L from [E|L]
    private int nextPivot(boolean[] colReduced) {
        int[] Lp = new int[M];
        int[] Lm = new int[M];
        for (Row el : rows) {
            for (int j=0; j<M; j++) {
                if (el.l[j] > 0)
                    Lp[j] += el.l[j];
                else
                    Lm[j] += -el.l[j];
            }
        }
        int pivot = -1, smallestValue = Integer.MAX_VALUE;
        for (int j=0; j<M; j++) {
            if (colReduced[j])
                continue;
            return j;
//            if (Lp[j]==0 && Lm[j]==0)
//                continue;
//            
//            int value = Lp[j] * Lm[j];
//            if (value < smallestValue) {
//                smallestValue = value;
//                pivot = j;
//            }
        }
        return pivot;
    }
    
    //-----------------------------------------------------------------------
    public void HilbertFM() {
//        for (int j=0; j<M; j++) {
        boolean[] colReduced = new boolean[M];
        int j;
        while ((j = nextPivot(colReduced)) >= 0) {
            colReduced[j] = true;
            if (verbose)
                System.out.println("next pivot: "+j);
            HilbertFMcol(j);
            if (verbose)
                System.out.println(this);
        }
    }
    
    //-----------------------------------------------------------------------
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append(String.format("%2d: ", i));
            sb.append(rows.get(i));
            sb.append("\n");
        }
        return sb.toString();
    }
    
    
    public static void main(String[] args) {
        int[][] matA1 = {{-2}, {3}};
        int[][] solA1 = { {3, 2} };
        
        int[][] mat33 = {
            { 1,  1,  0,  1,  1,  0,  1},
            { 1,  1,  1,  0,  1,  1,  1},
            { 1,  1,  1,  1,  0,  1,  0},
            {-1,  0, -1,  0,  0,  0,  0},
            {-1,  0,  0, -1,  0, -1, -1},
            {-1,  0,  0,  0, -1,  0,  0},
            { 0, -1, -1,  0,  0,  0, -1},
            { 0, -1,  0, -1,  0,  0,  0},
            { 0, -1,  0,  0, -1, -1,  0},
        };
        int[][] sol33 = {
            {0, 2, 1, 2, 1, 0, 1, 0, 2},
            {1, 0, 2, 2, 1, 0, 0, 2, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 2, 0, 0, 1, 2, 2, 0, 1},
            {2, 0, 1, 0, 1, 2, 1, 2, 0},            
        };
        
        int[][] mat44 = {
            { 1,  1,  1,  0,  1,  1,  1,  0,  1},
            { 1,  1,  1,  1,  0,  1,  1,  1,  1},
            { 1,  1,  1,  1,  1,  0,  1,  1,  1},
            { 1,  1,  1,  1,  1,  1,  0,  1,  0},
            {-1,  0,  0, -1,  0,  0,  0,  0,  0},
            {-1,  0,  0,  0, -1,  0,  0, -1,  0},
            {-1,  0,  0,  0,  0, -1,  0,  0, -1},
            {-1,  0,  0,  0,  0,  0, -1,  0,  0},
            { 0, -1,  0, -1,  0,  0,  0,  0,  0},
            { 0, -1,  0,  0, -1,  0,  0,  0, -1},
            { 0, -1,  0,  0,  0, -1,  0, -1,  0},
            { 0, -1,  0,  0,  0,  0, -1,  0,  0},
            { 0,  0, -1, -1,  0,  0,  0,  0, -1},
            { 0,  0, -1,  0, -1,  0,  0,  0,  0},
            { 0,  0, -1,  0,  0, -1,  0,  0,  0},
            { 0,  0, -1,  0,  0,  0, -1, -1,  0},
        };
        int[][] sol44 = {
            {0, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0},
            {0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0},
            {0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0},
            {0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1},
            {0, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0},
            {0, 0, 1, 1, 0, 1, 1, 0, 2, 0, 0, 0, 0, 1, 0, 1},
            {0, 0, 2, 0, 0, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 1},
            {0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0},
            {0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1},
            {0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 1, 0},
            {0, 1, 0, 1, 2, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1},
            {0, 2, 0, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 1},
            {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0},
            {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0},
            {1, 0, 0, 1, 0, 0, 1, 1, 1, 0, 1, 0, 0, 2, 0, 0},
            {1, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1, 0, 0, 2, 0},
            {1, 0, 1, 0, 0, 0, 0, 2, 0, 1, 1, 0, 1, 1, 0, 0},
            {1, 0, 1, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 1},
            {1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 2, 1, 0, 1, 0},
            {1, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 1},
        };
        
        /*int[][] mat55 = { // very hard, takes too much time
            { 1,  1,  1,  1,  0,  1,  1,  1,  1,  0,  1 },
            { 1,  1,  1,  1,  1,  0,  1,  1,  1,  1,  1 },
            { 1,  1,  1,  1,  1,  1,  0,  1,  1,  1,  1 },
            { 1,  1,  1,  1,  1,  1,  1,  0,  1,  1,  1 },
            { 1,  1,  1,  1,  1,  1,  1,  1,  0,  1,  0 },
            {-1,  0,  0,  0, -1,  0,  0,  0,  0,  0,  0 },
            {-1,  0,  0,  0,  0, -1,  0,  0,  0, -1,  0 },
            {-1,  0,  0,  0,  0,  0, -1,  0,  0,  0,  0 },
            {-1,  0,  0,  0,  0,  0,  0, -1,  0,  0, -1 },
            {-1,  0,  0,  0,  0,  0,  0,  0, -1,  0,  0 },
            { 0, -1,  0,  0, -1,  0,  0,  0,  0,  0,  0 },
            { 0, -1,  0,  0,  0, -1,  0,  0,  0,  0,  0 },
            { 0, -1,  0,  0,  0,  0, -1,  0,  0, -1, -1 },
            { 0, -1,  0,  0,  0,  0,  0, -1,  0,  0,  0 },
            { 0, -1,  0,  0,  0,  0,  0,  0, -1,  0,  0 },
            { 0,  0, -1,  0, -1,  0,  0,  0,  0,  0,  0 },
            { 0,  0, -1,  0,  0, -1,  0,  0,  0,  0, -1 },
            { 0,  0, -1,  0,  0,  0, -1,  0,  0,  0,  0 },
            { 0,  0, -1,  0,  0,  0,  0, -1,  0, -1,  0 },
            { 0,  0, -1,  0,  0,  0,  0,  0, -1,  0,  0 },
            { 0,  0,  0, -1, -1,  0,  0,  0,  0,  0, -1 },
            { 0,  0,  0, -1,  0, -1,  0,  0,  0,  0,  0 },
            { 0,  0,  0, -1,  0,  0, -1,  0,  0,  0,  0 },
            { 0,  0,  0, -1,  0,  0,  0, -1,  0,  0,  0 },
            { 0,  0,  0, -1,  0,  0,  0,  0, -1, -1,  0 },
        };*/
                        
//        testHilbert(matA1, solA1, false);
//        testHilbert(mat33, sol33, false);
//        testHilbert(mat44, sol44, false);
        //testHilbert(mat55, sol44, false);
        
        int[][] mat = {
            {-1,0}, {-1,1}, {0,-1}, {2,0}
        };
        testHilbert(mat, null, true);
    }
    
    private static void testHilbert(int[][] matIn, int[][] matRes, boolean keepCpCm) {
        HilbertBasis H = new HilbertBasis(matIn);  
        H.setVerbose();
        System.out.println(H);
        if (keepCpCm)
            H.setKeepCpCm();
        H.HilbertFM();
        System.out.println(H);
        
        if (matRes != null) {
            int[][] sol = new int[H.numRows()][];
            for (int i=0; i<sol.length; i++)
                sol[i] = H.getBasisVec(i);
            sortArrays(sol);
        
            sortArrays(matRes);
            boolean equal = true;
            for (int i=0; equal && i<sol.length; i++)
                equal = equal && Arrays.compare(sol[i], matRes[i])==0;
            System.out.println("Check solution: "+equal);
        }
    }
    
    private static void sortArrays(int[][] mat) {
        Comparator<int[]> comp = (int[] o1, int[] o2) -> Arrays.compare(o1, o2);
        Arrays.sort(mat, comp);
    }
}
