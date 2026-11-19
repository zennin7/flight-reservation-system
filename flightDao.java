public interface FlightDao {

    List<Flight> searchFlights(
        String originIata, 
        String destinationIata,
        LocalDate departureDate
    );

}
public class FlightDaoImpl implements FlightDao

@Override
public List<Flight> searchFlights(...)

