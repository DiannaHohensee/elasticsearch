/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.admin.cluster.allocation;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.master.MasterNodeRequest;
import org.elasticsearch.cluster.NodeWriteLoad;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.TimeValue;

import java.io.IOException;

public class NodeWriteLoadUpdateRequest extends MasterNodeRequest<NodeWriteLoadUpdateRequest> {
    private final NodeWriteLoad nodeWriteLoad;

    public NodeWriteLoadUpdateRequest(StreamInput in) throws IOException {
        super(in);
        this.nodeWriteLoad = new NodeWriteLoad(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.nodeWriteLoad.writeTo(out);
    }

    public NodeWriteLoadUpdateRequest(TimeValue masterNodeTimeout, NodeWriteLoad nodeWriteLoad) {
        super(masterNodeTimeout);
        this.nodeWriteLoad = nodeWriteLoad;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
