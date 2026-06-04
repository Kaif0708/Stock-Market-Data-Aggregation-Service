package com.stock.aggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleResponse {
    private String symbol;
    private String timeframe;
    private List<CandleDto> candles;
    private Integer count;
}
