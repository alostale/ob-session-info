package org.openbravo.util.session;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class SessionInfoReporter {

  private Properties obProps;
  private java.util.Date startDate;

  public SessionInfoReporter(String[] args) throws IOException, ParseException {
    String obDir = args[0];

    System.out.println("Base Openbravo path " + obDir);
    Path obPropertiesPath = Paths.get(obDir, "config", "Openbravo.properties");
    if (!Files.exists(obPropertiesPath)) {
      System.err
          .println("Parameter does not seem to be an Openbravo base path " + obPropertiesPath);
      throw new RuntimeException();
    }

    if (args.length > 1) {
      String date = args[1];
      startDate = new SimpleDateFormat("dd-MM-yyyy").parse(date);
    }

    obProps = new Properties();
    try (Reader r = new FileReader(obPropertiesPath.toAbsolutePath().toString())) {
      obProps.load(r);
    }

  }

  public static void main(String[] args)
      throws FileNotFoundException, IOException, SQLException, ParseException {
    long t = System.currentTimeMillis();
    new SessionInfoReporter(args).execute();
    System.out.println("Process executed in " + (System.currentTimeMillis() - t) + " ms");
  }

  private void execute() throws SQLException {
    // @formatter:off
    String query = 
      "select created, last_session_ping" + 
      "  from ad_session" +
      " where last_session_ping is not null";
    // @formatter:on

    Date dateParam = null;
    if (startDate != null) {
      System.out.println("Querying sessions since " + startDate);
      dateParam = new Date(startDate.getTime());
      System.out.println(dateParam);
      query += " and last_session_ping >= ?";
    } else {
      System.out.println("Querying all sessions");
    }
    query += " order by created";

    List<Event> events;
    try (Connection con = openConnection(); PreparedStatement stmt = con.prepareStatement(query)) {
      if (dateParam != null) {
        stmt.setDate(1, dateParam);
      }

      events = new ArrayList<>(5_000);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          Timestamp created = rs.getTimestamp("created");
          Timestamp lastPing = rs.getTimestamp("last_session_ping");
          events.add(new Event(created, 1));
          events.add(new Event(lastPing, -1));
        }
      }
    }
    Collections.sort(events);

    SimpleDateFormat sd = new SimpleDateFormat("dd-MM-yyyy");
    java.util.Date currentDate = null;
    int concurrentUsers = 0;
    int dayMaxUsers = 0;
    int dayTotal = 0;
    int absMax = 0;
    int absTotal = 0;
    Timestamp absMaxTs = null;
    Timestamp dayMaxTs = null;
    for (Event event : events) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(event.getTime());
      c.set(Calendar.HOUR_OF_DAY, 0);
      c.set(Calendar.MINUTE, 0);
      c.set(Calendar.SECOND, 0);
      c.set(Calendar.MILLISECOND, 0);
      java.util.Date d = c.getTime();
      if (currentDate == null || !currentDate.equals(d)) {
        if (currentDate != null) {
          System.out.println(sd.format(currentDate) + " - max concurrent users: " + dayMaxUsers
              + " (at " + dayMaxTs + ")- total created: " + dayTotal);
        }
        currentDate = d;
        dayMaxUsers = 0;
        dayTotal = 0;
      }

      concurrentUsers += event.getCnt();
      if (event.getCnt() == 1) {
        dayTotal += 1;
        absTotal += 1;
      }

      if (concurrentUsers > dayMaxUsers) {
        dayMaxUsers = Math.max(dayMaxUsers, concurrentUsers);
        dayMaxTs = event.getTimeStamp();
      }
      if (concurrentUsers > absMax) {
        absMax = concurrentUsers;
        absMaxTs = event.getTimeStamp();
      }
    }
    System.out.println(sd.format(currentDate) + " - max concurrent users: " + dayMaxUsers
        + " - total created: " + dayTotal);

    System.out.println("absolute: max: " + absMax + " (at " + absMaxTs + ") - total: " + absTotal);
  }

  private Connection openConnection() throws SQLException {
    String dbUrl = obProps.getProperty("bbdd.url") + "/" + obProps.getProperty("bbdd.sid");
    System.out.println("Connecting to " + dbUrl);
    Properties credentials = new Properties();
    credentials.put("user", obProps.getProperty("bbdd.user"));
    credentials.put("password", obProps.getProperty("bbdd.password"));

    return DriverManager.getConnection(dbUrl, credentials);
  }
}
