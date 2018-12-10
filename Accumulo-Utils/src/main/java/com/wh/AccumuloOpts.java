package com.wh;

import com.beust.jcommander.Parameter;

public class AccumuloOpts {
    @Parameter(names = "-home" , description = "HADOOP HOME, if not set, will be get from PATH")
    private String hadoopHome = null;
    @Parameter(names = "-zks",description = "zookeepers", required = true)
    private String zookeepers;
    @Parameter(names = "-instance", description = "the instance of accumulo", required = true)
    private String instance;
    @Parameter(names="-user", description = "the user of accumulo", required = true)
    private String user;
    @Parameter(names="-pwd", description = "the password of accumulo", required = true)
    private String pwd;
    @Parameter(names="-table", description = "the table name of accumulo", required = true)
    private String tableName;
    @Parameter(names="-output", description = "the path of result", required = true)
    private String output;

    public String getHadoopHome() {
        return hadoopHome;
    }

    public void setHadoopHome(String hadoopHome) {
        this.hadoopHome = hadoopHome;
    }

    public String getZookeepers() {
        return zookeepers;
    }

    public void setZookeepers(String zookeepers) {
        this.zookeepers = zookeepers;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
