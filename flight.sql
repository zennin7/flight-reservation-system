SELECT f.flight_id,
       f.flight_number,
       f.departure_time,
       f.arrival_time, 
       f.base_price

FROM flights f

JOIN airports o
    ON f.origin_airport_id = o.airport_id

JOIN airports d
    ON f.dest_airport_id = d.airport_id

WHERE o.iata_code = ?
  AND d.iata_code = ?
  AND f.departure_time::date = ?

ORDER BY f.departure_time ASC;