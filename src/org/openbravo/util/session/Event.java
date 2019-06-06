package org.openbravo.util.session;

import java.sql.Timestamp;

public class Event implements Comparable<Event> {

  private int cnt;
  private Timestamp ts;

  public Event(Timestamp ts, int cnt) {
    this.ts = ts;
    this.cnt = cnt;
  }

  public long getTime() {
    return ts.getTime();
  }

  @Override
  public int compareTo(Event o) {
    return Long.compare(getTime(), o.getTime());
  }

  public int getCnt() {
    return cnt;
  }

  public Timestamp getTimeStamp() {
    return ts;
  }
}
