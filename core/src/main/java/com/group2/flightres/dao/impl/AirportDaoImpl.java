package com.group2.flightres.dao.impl;

import com.group2.flightres.config.DatabaseConfig;
import com.group2.flightres.dao.AirportDao;
import com.group2.flightres.model.Airport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AirportDaoImpl implements AirportDao {

    private static final String FIND_ALL_QUERY =
            "SELECT iata_code, name, city, country, timezone FROM airports ORDER BY city ASC";

    @Override
    public List<Airport> findAll() {
        List<Airport> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_ALL_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("iata_code");
                String name = rs.getString("name");
                String city = rs.getString("city");
                String country = rs.getString("country");
                String tzStr = rs.getString("timezone");
                ZoneId zone = (tzStr != null && !tzStr.isBlank()) ? ZoneId.of(tzStr) : ZoneId.of("UTC");
                list.add(new Airport(code, name, city, country, zone));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch airports from database", e);
        }
        return list;
    }
}
