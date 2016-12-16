/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.runtime.controlprogram.parfor.opt;

import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.sysml.lops.LopProperties.ExecType;
import org.apache.sysml.runtime.DMLRuntimeException;
import org.apache.sysml.runtime.controlprogram.parfor.opt.OptNode.ParamType;
import org.apache.sysml.runtime.controlprogram.parfor.opt.PerfTestTool.TestMeasure;

/**
 * Base class for all potential cost estimators
 * 
 * TODO account for shared read-only matrices when computing aggregated stats
 * 
 */
public abstract class CostEstimator 
{
	
	protected static final Log LOG = LogFactory.getLog(CostEstimator.class.getName());
    
	
	//default parameters
	public static final double DEFAULT_EST_PARALLELISM = 1.0; //default degree of parallelism: serial
	public static final long   FACTOR_NUM_ITERATIONS   = 10; //default problem size
	public static final double DEFAULT_TIME_ESTIMATE   = 5;  //default execution time: 5ms
	public static final double DEFAULT_MEM_ESTIMATE_CP = 1024; //default memory consumption: 1KB 
	public static final double DEFAULT_MEM_ESTIMATE_MR = 10*1024*1024; //default memory consumption: 20MB // TODO investigate unused constant
	
	
	/**
	 * Main leaf node estimation method - to be overwritten by specific cost estimators
	 * 
	 * @param measure ?
	 * @param node internal representation of a plan alternative for program blocks and instructions
	 * @return estimate?
	 * @throws DMLRuntimeException if DMLRuntimeException occurs
	 */
	public abstract double getLeafNodeEstimate( TestMeasure measure, OptNode node ) 
		throws DMLRuntimeException;

	/**
	 * Main leaf node estimation method - to be overwritten by specific cost estimators
	 * 
	 * @param measure ?
	 * @param node internal representation of a plan alternative for program blocks and instructions
	 * @param et forced execution type for leaf node 
	 * @return estimate?
	 * @throws DMLRuntimeException if DMLRuntimeException occurs
	 */
	public abstract double getLeafNodeEstimate( TestMeasure measure, OptNode node, ExecType et ) 
		throws DMLRuntimeException;
	
	
	/////////
	//methods invariant to concrete estimator
	///
	
	/**
	 * Main estimation method.
	 * 
	 * @param measure ?
	 * @param node internal representation of a plan alternative for program blocks and instructions
	 * @return estimate?
	 * @throws DMLRuntimeException if DMLRuntimeException occurs
	 */
	public double getEstimate( TestMeasure measure, OptNode node ) 
		throws DMLRuntimeException
	{
		return getEstimate(measure, node, null);
	}
	
	/**
	 * Main estimation method.
	 * 
	 * @param measure ?
	 * @param node internal representation of a plan alternative for program blocks and instructions
	 * @param et execution type
	 * @return estimate?
	 * @throws DMLRuntimeException if DMLRuntimeException occurs
	 */
	public double getEstimate( TestMeasure measure, OptNode node, ExecType et ) 
		throws DMLRuntimeException
	{
		double val = -1;
		
		if( node.isLeaf() )
		{
			if( et != null )
				val = getLeafNodeEstimate(measure, node, et); //forced type
			else 
				val = getLeafNodeEstimate(measure, node); //default	
		}
		else
		{
			//aggreagtion methods for different program block types and measure types
			//TODO EXEC TIME requires reconsideration of for/parfor/if predicates 
			//TODO MEMORY requires reconsideration of parfor -> potential overestimation, but safe
			String tmp = null;
			double N = -1;
			switch ( measure )
			{
				case EXEC_TIME:
					switch( node.getNodeType() )
					{
						case GENERIC:
						case FUNCCALL:	
							val = getSumEstimate(measure, node.getChilds(), et); 
							break;
						case IF:
							if( node.getChilds().size()==2 )
								val = getWeightedEstimate(measure, node.getChilds(), et);
							else
								val = getMaxEstimate(measure, node.getChilds(), et); 
							break;
						case WHILE:
							val = FACTOR_NUM_ITERATIONS * getSumEstimate(measure, node.getChilds(), et); 
							break;
						case FOR:
							tmp = node.getParam(ParamType.NUM_ITERATIONS);
							N = (tmp!=null) ? (double)Long.parseLong(tmp) : FACTOR_NUM_ITERATIONS; 
							val = N * getSumEstimate(measure, node.getChilds(), et);
							break; 
						case PARFOR:
							tmp = node.getParam(ParamType.NUM_ITERATIONS);
							N = (tmp!=null) ? (double)Long.parseLong(tmp) : FACTOR_NUM_ITERATIONS; 
							val = N * getSumEstimate(measure, node.getChilds(), et) / node.getK(); 
							break;	
						default:
							//do nothing
					}
					break;
					
				case MEMORY_USAGE:
					switch( node.getNodeType() )
					{
						case GENERIC:
						case FUNCCALL:
						case IF:
						case WHILE:
						case FOR:
							val = getMaxEstimate(measure, node.getChilds(), et); 
							break;
						case PARFOR:
							if( node.getExecType() == OptNode.ExecType.MR )
								val = getMaxEstimate(measure, node.getChilds(), et); //executed in different JVMs
							else if ( node.getExecType() == OptNode.ExecType.CP )
								val = getMaxEstimate(measure, node.getChilds(), et) * node.getK(); //everything executed within 1 JVM
							break;
						default:
							//do nothing
					}
					break;
			}
		}
		
		return val;
	}

	protected double getDefaultEstimate(TestMeasure measure) 
	{
		double val = -1;
		
		switch( measure )
		{
			case EXEC_TIME: val = DEFAULT_TIME_ESTIMATE; break;
			case MEMORY_USAGE: val = DEFAULT_MEM_ESTIMATE_CP; break;
		}
		
		return val;
	}

	protected double getMaxEstimate( TestMeasure measure, ArrayList<OptNode> nodes, ExecType et ) 
		throws DMLRuntimeException
	{
		double max = Double.MIN_VALUE; //smallest positive value
		for( OptNode n : nodes )
		{
			double tmp = getEstimate( measure, n, et );
			if( tmp > max )
				max = tmp;
		}
		return max;
	}

	protected double getSumEstimate( TestMeasure measure, ArrayList<OptNode> nodes, ExecType et ) 
		throws DMLRuntimeException
	{
		double sum = 0;
		for( OptNode n : nodes )
			sum += getEstimate( measure, n, et );
		return sum;	
	}

	protected double getWeightedEstimate( TestMeasure measure, ArrayList<OptNode> nodes, ExecType et ) 
		throws DMLRuntimeException 
	{
		double ret = 0;
		int len = nodes.size();
		for( OptNode n : nodes )
			ret += getEstimate( measure, n, et );
		ret /= len; //weighting
		return ret;
	}
}
