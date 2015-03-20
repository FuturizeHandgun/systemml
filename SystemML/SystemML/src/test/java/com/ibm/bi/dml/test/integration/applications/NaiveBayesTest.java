/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2015
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.test.integration.applications;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.ibm.bi.dml.runtime.matrix.data.MatrixValue.CellIndex;
import com.ibm.bi.dml.test.integration.AutomatedTestBase;
import com.ibm.bi.dml.test.integration.TestConfiguration;
import com.ibm.bi.dml.test.utils.TestUtils;

@RunWith(value = Parameterized.class)
public class NaiveBayesTest  extends AutomatedTestBase{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2015\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	private final static String TEST_DIR = "applications/naive-bayes/";
	private final static String TEST_NAIVEBAYES = "naive-bayes";

	private int numRecords, numFeatures, numClasses;
    private double sparsity;
    
    public NaiveBayesTest(int rows, int cols, int nc, double sp) {
		numRecords = rows;
		numFeatures = cols;
		numClasses = nc;
		sparsity = sp;
	}
    
    @Parameters
	 public static Collection<Object[]> data() {
	   Object[][] data = new Object[][] { 
			   //sparse tests (sparsity=0.01)
			   {100, 50, 10, 0.01}, 
			   {1000, 500, 10, 0.01}, 
			   {10000, 750, 10, 0.01}, 
			   {100000, 1000, 10, 0.01},
			   //dense tests (sparsity=0.7)
			   {100, 50, 10, 0.7}, 
			   {1000, 500, 10, 0.7}, 
			   {10000, 750, 10, 0.7} 
			   };
	   
	   return Arrays.asList(data);
	 }
	 
	 @Override
	 public void setUp() {
		 setUpBase();
		 addTestConfiguration(TEST_NAIVEBAYES, new TestConfiguration(TEST_DIR, "naive-bayes",
	                new String[] { "prior", "conditionals" }));
	 }
	 
	 @Test
	 public void testNAIVEBAYES()
	 {
		 int rows = numRecords;
		 int cols = numFeatures;
		 int classes = numClasses;
		 double sparsity = this.sparsity;
		 double laplace_correction = 1;
	        
		 TestConfiguration config = getTestConfiguration(TEST_NAIVEBAYES);
	        
		 String NAIVEBAYES_HOME = SCRIPT_DIR + TEST_DIR;
		 fullDMLScriptName = NAIVEBAYES_HOME + TEST_NAIVEBAYES + ".dml";
		 programArgs = new String[]{"-stats", "-nvargs", 
				 "X=" + NAIVEBAYES_HOME + INPUT_DIR + "X", 
				 "Y=" + NAIVEBAYES_HOME + INPUT_DIR + "Y",
				 "classes=" + Integer.toString(classes),
				 "laplace=" + Double.toString(laplace_correction),
				 "prior=" + NAIVEBAYES_HOME + OUTPUT_DIR + "prior",
				 "conditionals=" + NAIVEBAYES_HOME + OUTPUT_DIR + "conditionals",
				 "accuracy=" + NAIVEBAYES_HOME + OUTPUT_DIR + "accuracy"};
			
		 fullRScriptName = NAIVEBAYES_HOME + TEST_NAIVEBAYES + ".R";
		 rCmd = "Rscript" + " " + 
				 fullRScriptName + " " + 
				 NAIVEBAYES_HOME + INPUT_DIR + " " + 
				 Integer.toString(classes) + " " + 
				 Double.toString(laplace_correction) + " " +
				 NAIVEBAYES_HOME + EXPECTED_DIR;
				
		 loadTestConfiguration(config);

		 double[][] X = getRandomMatrix(rows, cols, 0, 1, sparsity, -1);
		 double[][] Y = getRandomMatrix(rows, 1, 0, 1, 1, -1);
		 for(int i=0; i<rows; i++){
			 Y[i][0] = (int)(Y[i][0]*classes) + 1;
			 Y[i][0] = (Y[i][0] > classes) ? classes : Y[i][0];
	     }	
	        
		 writeInputMatrixWithMTD("X", X, true);
		 writeInputMatrixWithMTD("Y", Y, true);
	        
		 runTest(true, false, null, -1);
	        
		 runRScript(true);
		 disableOutAndExpectedDeletion();
	        
		 HashMap<CellIndex, Double> priorR = readRMatrixFromFS("prior");
		 HashMap<CellIndex, Double> priorDML= readDMLMatrixFromHDFS("prior");
		 HashMap<CellIndex, Double> conditionalsR = readRMatrixFromFS("conditionals");
		 HashMap<CellIndex, Double> conditionalsDML = readDMLMatrixFromHDFS("conditionals");
		 boolean success = 
				 TestUtils.compareMatrices(priorR, priorDML, Math.pow(10, -12), "priorR", "priorDML")
				 && TestUtils.compareMatrices(conditionalsR, conditionalsDML, Math.pow(10.0, -12.0), "conditionalsR", "conditionalsDML");
		 System.out.println(success+"");
	 }
}