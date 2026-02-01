package io.regulyn.connector.adapter;

import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorJob;

public interface ConnectorAdapter {

    ConnectorExecutionResult executeDelete(Connector connector, ConnectorJob job);

    ConnectorExecutionResult executeExport(Connector connector, ConnectorJob job);

    ConnectorExecutionResult executeAuditPull(Connector connector, ConnectorJob job);
}
