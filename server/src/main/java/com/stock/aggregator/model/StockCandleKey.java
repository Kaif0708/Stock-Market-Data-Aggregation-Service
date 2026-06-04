package com.stock.aggregator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;

@PrimaryKeyClass
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockCandleKey implements Serializable {

    @PrimaryKeyColumn(name = "symbol", type = PrimaryKeyType.PARTITIONED)
    private String symbol;

    @PrimaryKeyColumn(name = "datetime", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant datetime;
}
