/*******************************************************************************
 * Copyright (c) 2020 Konduit K.K.
 * Copyright (c) 2015-2019 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package com.aquila.chess.strategy.mcts.nnImpls.agz;

import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataType;

/**
 * Define and load an AlphaGo Zero dual ResNet architecture
 * into DL4J.
 *
 * The dual residual architecture is the strongest
 * of the architectures tested by DeepMind for AlphaGo
 * Zero. It consists of an initial convolution layer block,
 * followed by a number (40 for the strongest, 20 as
 * baseline) of residual blocks. The network is topped
 * off by two "heads", one to predict policies and one
 * for value functions.
 *
 * @author Max Pumperla
 */
public class DualResnetModel {

    public static ComputationGraph getModel(final int blocks, final int numPlanes) {

        final DL4JAlphaGoZeroBuilder builder = new DL4JAlphaGoZeroBuilder(new int[]{3, 3}, new int[]{1, 1}, ConvolutionMode.Same, numPlanes);
        final String input = "in";

        builder.addInputs(input);
        final String initBlock = "init";
        final String convOut = builder.addConvBatchNormBlock(initBlock, input, numPlanes, true);
        final String towerOut = builder.addResidualTower(blocks, convOut);
        String policyOut = builder.addPolicyHead(towerOut, true);
        final String valueOut = builder.addValueHead(towerOut, true);
        builder.addOutputs(policyOut, valueOut);

        final ComputationGraph model = new ComputationGraph(builder.buildAndReturn());
        model.init();

        return model;
    }
}
