/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.FloatBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.LongFloatTupleBlockSourceOperator;
import org.elasticsearch.compute.operator.SourceOperator;
import org.elasticsearch.core.Tuple;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.equalTo;

public class MaxFloatGroupingAggregatorFunctionTests extends GroupingAggregatorFunctionTestCase {

    @Override
    protected SourceOperator simpleInput(BlockFactory blockFactory, int end) {
        return new LongFloatTupleBlockSourceOperator(
            blockFactory,
            LongStream.range(0, end)
                .mapToObj(l -> Tuple.tuple(randomLongBetween(0, 4), randomFloatBetween(-Float.MAX_VALUE, Float.MAX_VALUE, true)))
        );
    }

    @Override
    protected AggregatorFunctionSupplier aggregatorFunction() {
        return new MaxFloatAggregatorFunctionSupplier();
    }

    @Override
    protected String expectedDescriptionOfAggregator() {
        return "max of floats";
    }

    @Override
    protected void assertSimpleGroup(List<Page> input, Block result, int position, Long group) {
        Optional<Float> max = input.stream().flatMap(p -> allFloats(p, group)).max(floatComparator());
        if (max.isEmpty()) {
            assertThat(result.isNull(position), equalTo(true));
            return;
        }
        assertThat(result.isNull(position), equalTo(false));
        assertThat(((FloatBlock) result).getFloat(position), equalTo(max.get()));
    }
}
