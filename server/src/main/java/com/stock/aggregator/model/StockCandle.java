package com.stock.aggregator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("stock_candles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockCandle {

    @PrimaryKey
    private StockCandleKey key;

    @Column("open")
    private Double open;

    @Column("high")
    private Double high;

    @Column("low")
    private Double low;

    @Column("close")
    private Double close;

    @Column("volume")
    private Long volume;
}
