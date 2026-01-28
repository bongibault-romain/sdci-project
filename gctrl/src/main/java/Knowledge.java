import org.h2.tools.DeleteDbFiles;

import java.sql.*;
import java.util.*;

//

//* @author couedrao on 25/11/2019.

//* @project gctrl

//

//

//* 1)Standard data shared among the monitor analyze plan and Standard data shared among the monitor, analyze, plan and execute functions

//* 2)The shared knowledge includes data such as topology information, historical logs, metrics, symptoms and policies

//* 3)Created by the monitor part while execute part might update the knowledge

//*

class Knowledge {

    private static final String DB_DRIVER = "org.h2.Driver";
    private static final String DB_CONNECTION = "jdbc:h2:~/test";
    private static final String DB_USER = "";
    private static final String DB_PASSWORD = "";

    static final int moving_wind = 10;
    static final int horizon = 3;
    static final String gw = "GW_I";
    static final double gw_debit_upgrade_threshold = 1;
    static final double gw_debit_downgrade_threshold = 0.9;

    /*TODO : edit symptom, rfc, workflow_lists, plan*/
    private static final List<String> symptom = Arrays.asList("N/A", "DOWNGRADE", "OK", "UPGRADE");
    private static final List<String> rfc = Arrays.asList("DoNotDoAnything", "DeleteReplicas", "AddReplicas");
    private static final List<String> workflow_lists = Arrays.asList("UC1", "UC2", "UC3");
    private static final List<String> plan = Arrays.asList("A", "B", "C");
    private final Map<String, String> gwinfo = new HashMap<>();
    private final List<Map<String, String>> gwsinfo = new ArrayList<>();
    private final String olddestip = "192.168.0.2";
    private String newdestip;
    private String oldgwip;
    private String lbip;
    private List<String> newgwsip;
    private final String importantsrcip = "192.168.0.1";
    public static int replicas = 1;
    public static int MAXIMUM_REPLICAS = 8;

    public static final double GATEWAY_MAXIMUM_OUTPUT_DEBIT = 2.0; // RPS

    void start() throws Exception {
        // delete the H2 database named 'test' in the user home directory
        DeleteDbFiles.execute("~", "test", true);
        Main.logger(this.getClass().getSimpleName(), "old database 'test' deleted");
        //Initialization of the Knowledge
        Knowledge.replicas = MANOAPI.get_pod_replicas("sdci-gwi");

        Main.logger(this.getClass().getSimpleName(), "Number of replicas at the Knowledge initialization: " + Knowledge.replicas);

        store_symptoms();
        store_rfcs();
        store_plans();
        store_execution_workflow();

        gwinfo.put("name", "gwi");
        gwinfo.put("image", "sdci-gateway:v2");
        //gwinfo.put("net", "new_network");

        gwsinfo.add(0, gwinfo);
        gwsinfo.add(1, gwinfo);
        //gwsinfo.add(2, gwinfo);

        Main.logger(this.getClass().getSimpleName(), "Knowledge Starting");

    }

    void insert_in_tab(Timestamp timestamp, double lat) {
        try (Connection conn = getDBConnection()) {
            PreparedStatement insert;
            String InsertQuery = "INSERT INTO " + Knowledge.gw + "_LAT" + " (id, latency) values" + "(?,?)";
            conn.setAutoCommit(false);
            insert = conn.prepareStatement(InsertQuery);
            insert.setTimestamp(1, timestamp);
            insert.setDouble(2, lat);
            insert.executeUpdate();
            insert.close();
            conn.commit();
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<String> get_symptoms() {
        String gw_symp = gw + "_SYMP";

        Connection conn = getDBConnection();
        String SelectQuery = "select * from " + gw_symp;
        PreparedStatement select;
        List<String> r = null;
        try {
            select = conn.prepareStatement(SelectQuery);
            ResultSet rs = select.executeQuery();
            r = new ArrayList<>();
            while (rs.next()) {
                r.add(rs.getString("symptom"));
            }
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return r;

    }

    List<String> get_rfc() {
        String gw_rfc = gw + "_RFC";

        Connection conn = getDBConnection();
        String SelectQuery = "select * from " + gw_rfc;
        PreparedStatement select;
        List<String> r = null;
        try {
            select = conn.prepareStatement(SelectQuery);
            ResultSet rs = select.executeQuery();
            r = new ArrayList<>();
            while (rs.next()) {
                r.add(rs.getString("rfc"));
            }
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return r;

    }

    List<String> get_plans() {
        String gw_plan = gw + "_PLAN";

        Connection conn = getDBConnection();
        String SelectQuery = "select * from " + gw_plan;
        PreparedStatement select;
        List<String> r = null;
        try {
            select = conn.prepareStatement(SelectQuery);
            ResultSet rs = select.executeQuery();
            r = new ArrayList<>();
            while (rs.next()) {
                r.add(rs.getString("plan"));
            }
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return r;

    }

    List<String> get_worklow_lists() {
        String gw_execw = gw + "_EXECW";

        Connection conn = getDBConnection();
        String SelectQuery = "select * from " + gw_execw;
        PreparedStatement select;
        List<String> r = null;
        try {
            select = conn.prepareStatement(SelectQuery);
            ResultSet rs = select.executeQuery();
            r = new ArrayList<>();
            while (rs.next()) {
                r.add(rs.getString("workflow"));
            }
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return r;

    }

    ResultSet select_from_tab() {
        return this.select_from_tab(moving_wind);
    }

    ResultSet select_from_tab(int top) {
        //Main.logger("Select the last " + n + " latencies");
        Connection conn = getDBConnection();
        String SelectQuery = "select TOP " + top + " * from " + Knowledge.gw + "_LAT" + " ORDER BY id DESC";
        //PreparedStatement select;
        ResultSet rs = null;
        try {
            Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            // select = conn.prepareStatement(SelectQuery);
            rs = stmt.executeQuery(SelectQuery);
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rs;


    }

    void create_lat_tab() {
        try (Connection conn = getDBConnection()) {
            Statement create;
            conn.setAutoCommit(false);
            create = conn.createStatement();
            create.execute("CREATE TABLE " + Knowledge.gw + "_LAT" + " (id timestamp primary key, latency double )");
            create.close();
            conn.commit();
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Main.logger(this.getClass().getSimpleName(), "... Database Created");

        }
    }

    private void store_plans() throws SQLException {
        String gw_plan = gw + "_PLAN";
        Connection conn = getDBConnection();
        Statement create;
        conn.setAutoCommit(false);
        create = conn.createStatement();
        create.execute("CREATE TABLE " + gw_plan + " (id int primary key, plan varchar(20) )");
        create.close();

        for (int i = 0; i < plan.size(); i++) {
            conn = getDBConnection();
            PreparedStatement insert;
            try {
                insert = conn.prepareStatement("INSERT INTO " + gw_plan + " (id, plan) values" + "(?,?)");
                insert.setInt(1, i + 1);
                insert.setString(2, plan.get(i));
                insert.executeUpdate();
                insert.close();
                conn.commit();
            } catch (SQLException e) {
                System.out.println("Exception Message " + e.getLocalizedMessage());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                conn.close();
            }
        }
    }

    private void store_rfcs() throws SQLException {
        String gw_rfc = gw + "_RFC";
        Connection conn = getDBConnection();
        Statement create;
        conn.setAutoCommit(false);
        create = conn.createStatement();
        create.execute("CREATE TABLE " + gw_rfc + " (id int primary key, rfc varchar(40) )");
        create.close();

        for (int i = 0; i < rfc.size(); i++) {
            conn = getDBConnection();
            PreparedStatement insert;
            try {
                insert = conn.prepareStatement("INSERT INTO " + gw_rfc + " (id, rfc) values" + "(?,?)");
                insert.setInt(1, i + 1);
                insert.setString(2, rfc.get(i));
                insert.executeUpdate();
                insert.close();
                conn.commit();
            } catch (SQLException e) {
                System.out.println("Exception Message " + e.getLocalizedMessage());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                conn.close();
            }
        }
    }

    private void store_execution_workflow() throws SQLException {
        String gw_execw = gw + "_EXECW";
        Connection conn = getDBConnection();
        Statement create;
        conn.setAutoCommit(false);
        create = conn.createStatement();
        create.execute("CREATE TABLE " + gw_execw + " (id int primary key, workflow varchar(50) )");
        create.close();

        for (int i = 0; i < workflow_lists.size(); i++) {
            conn = getDBConnection();
            PreparedStatement insert;
            try {
                insert = conn.prepareStatement("INSERT INTO " + gw_execw + " (id, workflow) values" + "(?,?)");
                insert.setInt(1, i + 1);
                insert.setString(2, workflow_lists.get(i));
                insert.executeUpdate();
                insert.close();
                conn.commit();
            } catch (SQLException e) {
                System.out.println("Exception Message " + e.getLocalizedMessage());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                conn.close();
            }
        }
    }

    private void store_symptoms() throws SQLException {
        String gw_symp = gw + "_SYMP";
        Connection conn = getDBConnection();
        Statement create;
        conn.setAutoCommit(false);
        create = conn.createStatement();
        create.execute("CREATE TABLE " + gw_symp + " (id int primary key, symptom varchar(50) )");
        create.close();

        for (int i = 0; i < symptom.size(); i++) {
            conn = getDBConnection();
            PreparedStatement insert;

            try {
                insert = conn.prepareStatement("INSERT INTO " + gw_symp + " (id, symptom) values" + "(?,?)");
                insert.setInt(1, i + 1);
                insert.setString(2, symptom.get(i));
                insert.executeUpdate();
                insert.close();
                conn.commit();
            } catch (SQLException e) {
                System.out.println("Exception Message " + e.getLocalizedMessage());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                conn.close();
            }
        }
    }

    private Connection getDBConnection() {
        // Main.logger("Connecting the database ...");
        try {
            Class.forName(DB_DRIVER);
        } catch (ClassNotFoundException e) {
            System.out.println(e.getMessage());
        }
        try {
            return DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        }

    }

    public Map<String, String> getGwinfo() {
        return gwinfo;
    }

    public List<Map<String, String>> getGwsinfo() {
        return gwsinfo;
    }

    public String getOlddestip() {
        return olddestip;
    }

    public String getNewdestip() {
        return newdestip;
    }

    public void setNewdestip(String newdestip) {
        this.newdestip = newdestip;
    }

    public String getOldgwip() {
        return oldgwip;
    }

    public void setOldgwip(String oldgwip) {
        this.oldgwip = oldgwip;
    }

    public String getLbip() {
        return lbip;
    }

    public void setLbip(String lbip) {
        this.lbip = lbip;
    }

    public List<String> getNewgwsip() {
        return newgwsip;
    }

    public void setNewgwsip(List<String> newgwsip) {
        this.newgwsip = newgwsip;
    }

    public String getImportantsrcip() {
        return importantsrcip;
    }

    public double[] get_history(int amount) throws SQLException {
        ResultSet rs = this.select_from_tab(amount);

        rs.first();
        List<Double> temp = new ArrayList<>();
        while (rs.next()) {
            temp.add(Double.parseDouble(rs.getString("latency")));
        }

        return temp.stream().mapToDouble(Double::doubleValue).toArray();
    }
}