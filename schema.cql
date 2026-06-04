-- CREATE KEYSPACE for Stock Market Data Aggregation Service
CREATE KEYSPACE IF NOT EXISTS stock_keyspace 
WITH replication = {
    'class': 'SimpleStrategy', 
    'replication_factor': 1
};

USE stock_keyspace;

-- CREATE TABLE for storing raw 1-minute stock candles.
-- symbol is the Partition Key (queries will filter by symbol).
-- datetime is the Clustering Key, ordered DESC to fetch the latest records first.
CREATE TABLE IF NOT EXISTS stock_candles (
    symbol varchar,
    datetime timestamp,
    open double,
    high double,
    low double,
    close double,
    volume bigint,
    PRIMARY KEY (symbol, datetime)
) WITH CLUSTERING ORDER BY (datetime DESC);
