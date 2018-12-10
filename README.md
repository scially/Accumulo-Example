原文地址: [http://accumulo.apache.org/1.7/accumulo_user_manual.html](http://accumulo.apache.org/1.7/accumulo_user_manual.html)
## 4. Writing Accumulo Clients
### 4.1. Running Clinet Code
有3种方式来运行使用Accumulo提供的API开发的Accumulo客户端：

    1. 使用`java -jar jar`包
    2. 使用`accmulo jar`
    3. 使用bin目录下的tool.sh脚本来运行
    
为了使用Accumulo，你需要在classpath中配置你的环境变量。Accumulo依赖于Hadoop和Zookeeper，对于Hadoop，Hadoop所有的jar包都存在lib目录下面，配置文件都存在conf目录下；对于新版本的Zookeep，你只需要添加Zookee的jar包即可，而不需要将Zookeeper的lib目录下jar包全都添加。你可以使用`$ACCUMULO_HOME/bin/accumulo classpath`来查看Accumulo所依赖的jar包。

对于上述执行的第2种方法，你可以将自己写的jar包放在`/lib/exe`下，然后可以直接使用accumulo来执行。例如：假设你写了一个类`com.foo.Client`，打成了jar包，并把它放在了`lib/ext`中，那么你可以直接执行`accumulo com.foo.Client`执行你写的代码。

如果你使用Hadoop的MapReduceJob，并且使用了Accumulo，那么你可以通过`bin/tool.sh`来运行这个job。参见`map reduce example`。

### 4.2.Connecting
所有的Accumulo客户端首先要创建Accumulo instance。代码如下：
``` java
String instanceName = "myinstance";
String zooServers = "zooserver-one,zooserver-two"
Instance inst = new ZooKeeperInstance(instanceName, zooServers);

Connector conn = inst.getConnector("user", new PasswordToken("passwd"));
```
其中`PasswordToken`是`AuthenticationToken`的一个常见实现类。`AuthenticationToken`这个接口允许用户通过不同的形式（子类）来认证。The CredentialProviderToken leverages the Hadoop CredentialProviders (new in Hadoop 2.6).

For example, the CredentialProviderToken can be used in conjunction with a Java KeyStore to alleviate passwords stored in cleartext. When stored in HDFS, a single KeyStore can be used across an entire instance. Be aware that KeyStores stored on the local filesystem must be made available to all nodes in the Accumulo cluster.

```
KerberosToken token = new KerberosToken();
Connector conn = inst.getConnector(token.getPrincipal(), token);
```

The KerberosToken can be provided to use the authentication provided by Kerberos. Using Kerberos requires external setup and additional configuration, but provides a single point of authentication through HDFS, YARN and ZooKeeper and allowing for password-less authentication with Accumulo.

### 4.3.Writing Data
所有写入Accumulo的数据都通过Mutation写入，Mutation代表了一行所有族的数据变化。
Mutation可以这样创建（注意：此时只是将数据写入Mutation，并未写入Accumulo中）：
```
Text rowID = new Text("row1");
Text colFam = new Text("myColFam");
Text colQual = new Text("myColQual");
ColumnVisibility colVis = new ColumnVisibility("public");
long timestamp = System.currentTimeMillis();

Value value = new Value("myValue".getBytes());

Mutation mutation = new Mutation(rowID);
mutation.put(colFam, colQual, colVis, timestamp, value);
```

### 4.3.1.BatchWriter
BarchWriter负责将多个Mutation写入Accumulo中。BatchWriter是一种效率高的写入对象，它每次发送多个Mutations到多个TabletServers，并且自动的多次发送Mutations到相同的TabletServer以分摊网络开销。要注意的是，由于BatcherWriter是将Mutations存在内存中，所以要尽量避免任何一个到BatcherWriter中后内容发生变化。
Mutations可以这样添加到BatchWriter:
```
// BatchWriterConfig has reasonable defaults
BatchWriterConfig config = new BatchWriterConfig();
config.setMaxMemory(10000000L); // bytes available to batchwriter for buffering mutations

BatchWriter writer = conn.createBatchWriter("table", config)

writer.addMutation(mutation);

writer.close();
```
在`accumulo/docs/examples/README.batch`中有batch writer的例子。

### 4.3.2.ConditionalWriter
ConditionalWriter会对每一行开启有效，原子读写的操作。

### 4.3.3.Durability
通常，Accumulo首先会把所有写入操作全部更新到WAL中。每次改变都会反映到HDFS文件系统，并会同步到硬盘中以持久化。在某次失败事件中，在内存中的数据会被WAL中的数据替换。在HDFS中的文件都是以块存储，并且是有多个副本。发送更新到副本，并等待同步到磁盘可以显提高写入速度。

Accumulo允许用户在写入数据是使用不同的形式来持久化数据：
* none: 不使用WAL，没有持久化数据
* log: WAL被使用，但是不会刷新。服务器发生异常可能会导致最新的数据丢失
* flush: 更新被写入到WAL，并刷新到副本，服务器异常不太可能导致数据丢失
* sync:更新被写入WAL，并更新所有副本到磁盘。即使整个集群挂掉，数据也不会丢失。

用户可以设置默认持久特性在shell中。当写入数据时，用户可以配置BatchWriter或者ConditionWriter使用不同级别的持久特性，这会替换默认设置：

```
BatchWriterConfig cfg = new BatchWriterConfig();
// We don't care about data loss with these writes:
// This is DANGEROUS:
cfg.setDurability(Durability.NONE);

Connection conn = ... ;
BatchWriter bw = conn.createBatchWriter(table, cfg);
```

## 4.4.Reading Data
Accumulo在根据key取数据时效率很高，也可以根据连续的key范围返回这个区间的值。

### 4.4.1.Scanner

使用Scanner取数据，它就像一个迭代器。Scanners也可以根据开始键值和终止键值返回一系列的列。
```
// specify which visibilities we are allowed to see
Authorizations auths = new Authorizations("public");

Scanner scan =
    conn.createScanner("table", auths);

scan.setRange(new Range("harry","john"));
scan.fetchColumnFamily(new Text("attributes"));

for(Entry<Key,Value> entry : scan) {
    Text row = entry.getKey().getRow();
    Value value = entry.getValue();
}
```
### 4.4.2.Isolated Scanner

### 4.4.3.BatchScanner
对于某些类型的读取，同时检索多个范围数据更有效。例如，当访问一组不连续的行时，会出现这种情况，这些行的ID已从二级索引中检索到。
BatchScanner配置与Scanner相似。它需要传入多个范围，而不是一个范围。注意，BatchScanner返回的key不是有序的，因为流传输的key是从多个TabletServers得到的。
```
ArrayList<Range> ranges = new ArrayList<Range>();
// populate list of ranges ...

BatchScanner bscan =
    conn.createBatchScanner("table", auths, 10);
bscan.setRanges(ranges);
bscan.fetchColumnFamily("attributes");

for(Entry<Key,Value> entry : bscan) {
    System.out.println(entry.getValue());
}
```
参见`accumulo\docs\examples\README.batch`

4.5. Proxy
代理API可以让用户使用其他语言来与Accumulo交互。Accumulo中提供了代理服务器，客户端可以进一步被生成。代理API可以来代替传统的ZookeeperInstance来提供TCP端口（需要防火墙开启）来与Accumulo通信，而不需要与集群的TabletServer直接通信。

### 4.5.1.Prerequisites

代理服务器可以放在任何一个节点（首先要保证可以与Accumulo通信），然后他可以与Master、Zookeeper、NameNode和DataNodes通信。代理客户端只需要和代理服务器通信即可。

### 4.5.2.Configuration
代理服务器的配置文件如下，你也至少需要提供如下配置：
```
protocolFactory=org.apache.thrift.protocol.TCompactProtocol$Factory
tokenClass=org.apache.accumulo.core.client.security.tokens.PasswordToken
port=42424
instance=test
zookeepers=localhost:2181
```

你可以在`$ACCUMULO_HOME/proxy/proxy.properties`找到配置文件示例。

此示例配置文件进一步演示了如何通过MockAccumulo或MiniAccumuloCluster连接代理服务器。

### 4.5.3.Running the Proxy Server
在创建好配置文件后，可以使用如下命令来启用代理（假设你的配置文件名为:`config.properties`）
```
$ACCUMULO_HOME/bin/accumulo proxy -p config.properties
```

### 4.5.4.Creating a Proxy Client

## 5.Development Clients

通常，Accumulo包含了大量很多依赖环境。即使是一个单机环境，Accumulo也需要Hadoop、Zookeeper，Accumulo Master，一个Tablet Server等。如果你想使用Accumulo来写一个单元测试，你需要首先配置大量环境。

### 5.1.Mock Accumulo
MockAccumulo支持大量的伪ClientAPI，无需登录、权限等。同时也支持迭代器和结合器。注意：MockAccumulo将所有数据存放在内存中，因此在运行期间不会保留任何数据和设置。

正常连接方式是：
```
Instance instance = new ZooKeeperInstance(...);
Connector conn = instance.getConnector(user, passwordToken);
```
为了与MockAccumulo交互，使用MockInstance来代理ZookeeperInstance:
```
Instance instance = new MockInstance();
```
在shell中，你也可以用`--fake`选项启用MockAccumulo：
```
$ ./bin/accumulo shell --fake -u root -p ''

Shell - Apache Accumulo Interactive Shell
-
- version: 1.6
- instance name: fake
- instance id: mock-instance-id
-
- type 'help' for a list of available commands
-

root@fake> createtable test

root@fake test> insert row1 cf cq value
root@fake test> insert row2 cf cq value2
root@fake test> insert row3 cf cq value3

root@fake test> scan
row1 cf:cq []    value
row2 cf:cq []    value2
row3 cf:cq []    value3

root@fake test> scan -b row2 -e row2
row2 cf:cq []    value2

root@fake test>
```

当使用MapReduceJob时，你也可以设置MockAccmumlo的输入和输出：
```
AccumuloInputFormat.setMockInstance(job, "mockInstance");
AccumuloOutputFormat.setMockInstance(job, "mockInstance");
```
## 5.2.Mini Accumulo Cluster
为了单元测试，Mock Accumulo已经提供了轻量级的Accumulo Client API实现，但是大多数情况还是希望模拟一个更加真实的集群环境下的Accumulo环境，此时MiniAccumulo  Cluster就派上了用场，通过一些配置（包括Zookeeper等）就可以在本地系统启动集群。
为了使用MiniAccumulo Cluster，你首先要创建一个空的目录和传入一个密码做为参数：
```java
File tempDirectory = // JUnit and Guava supply mechanisms for creating temp directories
MiniAccumuloCluster accumulo = new MiniAccumuloCluster(tempDirectory, "password");
accumulo.start();
```
一旦上述代码执行完毕，我们就可以使用模拟的集群环境
```
Instance instance = new ZooKeeperInstance(accumulo.getInstanceName(), accumulo.getZooKeepers());
Connector conn = instance.getConnector("root", new PasswordToken("password"));
```

停止模拟集群下的Accumulo
```
accumulo.stop();
```
## Appendix: 权限控制
[https://accumulo.apache.org/1.7/examples/visibility](https://accumulo.apache.org/1.7/examples/visibility)
#### 用户权限
1. 首先我们先创建一个用户
```bash
root@accumulo[instance]> createuser hwang
2018-12-10 10:36:33,722 [Shell.audit] INFO : root@accumulo> createuser hwang
Enter new password for 'hwang': ********
Please confirm new password for 'hwang': ********
```
2. 切换到hwang用户，然后创建一个表
```bash
root@accumulo> user hwang
2018-12-10 10:37:59,385 [Shell.audit] INFO : root@accumulo> user hwang
Enter password for user hwang: ********
hwang@accumulo> create
createnamespace   createtable       createuser
hwang@accumulo> createtable test
2018-12-10 10:38:11,687 [Shell.audit] INFO : hwang@accumulo> createtable test
2018-12-10 10:38:11,758 [shell.Shell] ERROR: org.apache.accumulo.core.client.AccumuloSecurityException: Error PERMISSION_DENIED for user hwang on table test(?) - User does not have permission to perform this action
```
提示我们没有这个权限去创建，对于Accumulo来说，用户默认是没有创建表的权限的，所以我们就要进行授权
#### 授权
1. 授权必须在root账户下进行。
```bash
hwang@accumulo> user root
2018-12-10 10:39:51,260 [Shell.audit] INFO : hwang@accumulo> user root
Enter password for user root: *****
root@accumulo> grant -s System.
System.ALTER_NAMESPACE           System.ALTER_TABLE               System.ALTER_USER
System.CREATE_NAMESPACE          System.CREATE_TABLE              System.CREATE_USER
System.DROP_NAMESPACE            System.DROP_TABLE                System.DROP_USER
System.GRANT                     System.OBTAIN_DELEGATION_TOKEN   System.SYSTEM
root@accumulo> grant -s System.CREATE_TABLE -u hwang
2018-12-10 10:40:09,700 [Shell.audit] INFO : root@accumulo> grant -s System.CREATE_TABLE -u hwang
root@accumulo> createtable test
2018-12-10 10:40:17,069 [Shell.audit] INFO : root@accumulo> createtable test
root@accumulo test> user hwang
2018-12-10 10:41:05,779 [Shell.audit] INFO : root@accumulo test> user hwang
Enter password for user hwang: ********
hwang@accumulo test> userpermissions
2018-12-10 10:41:10,866 [Shell.audit] INFO : hwang@accumulo test> userpermissions
System permissions: System.CREATE_TABLE
Namespace permissions (accumulo): Namespace.READ
Table permissions (accumulo.metadata): Table.READ
Table permissions (accumulo.replication): Table.READ
Table permissions (accumulo.root): Table.READ
```
可以看到在我们创建用户的时候，用户已经有了读的权限，然后我们增加了一个创建表的权限。

#### 可视性
1. 英文中是visibilitiy，我简单翻译为可视性，Accumulo中可以对每一条记录进行控制，只有拥有响应的认证（Authorization）才可以看到。
2. Visibilitiy和Authorization，我觉得是一个概念，只是Visibilitiy针对的是数据，Authorization针对的是用户，当我们插入数据时，
我们可以用Visibilitiy，当我们读数据时我们的用户需要对应的Authorization
3. 实际上Authorization或者Visibilitiy就是一些&和|组成的表达式，比如“A|B”或者"(A|B)&C"
4. 我们插入几条数据
```bash
hwang@accumulo test> user root
2018-12-10 11:01:56,121 [Shell.audit] INFO : hwang@accumulo test> user root
Enter password for user root: *****
root@accumulo test> grant -s Table.WRITE -u hwang
2018-12-10 11:01:58,985 [Shell.audit] INFO : root@accumulo test> grant -s Table.WRITE -u hwang
root@accumulo test> user hwang
2018-12-10 11:02:03,320 [Shell.audit] INFO : root@accumulo test> user hwang
Enter password for user hwang: ********
hwang@accumulo test> insert row f1 q1 v1 -l A
2018-12-10 11:02:09,280 [Shell.audit] INFO : hwang@accumulo test> insert row f1 q1 v1 -l A
hwang@accumulo test> insert row f2 q2 v2 -l A&B
2018-12-10 11:02:44,759 [Shell.audit] INFO : hwang@accumulo test> insert row f2 q2 v2 -l A&B
hwang@accumulo test> insert row f3 q3 v3 -l apple&carrot|broccoli|spinach
2018-12-10 11:03:04,912 [Shell.audit] INFO : hwang@accumulo test> insert row f3 q3 v3 -l apple&carrot|broccoli|spinach
2018-12-10 11:03:04,912 [shell.Shell] ERROR: org.apache.accumulo.core.util.BadArgumentException: cannot mix | and & near index 12
apple&carrot|broccoli|spinach
            ^
hwang@accumulo test> insert row f3 q3 v3 -l (apple&carrot)|broccoli|spinach
2018-12-10 11:03:13,692 [Shell.audit] INFO : hwang@accumulo test> insert row f3 q3 v3 -l (apple&carrot)|broccoli|spinach
```
这里要注意的是在|和&混用的时候必须用括号指定优先级。
可以看到我们这里插入了一行数据，然后有三个族，分别有这不同的可视性，然后我们分别用不同的认证来读取下数据。
#### 读取数据
```bash
hwang@accumulo test> scan
2018-12-10 11:14:21,078 [Shell.audit] INFO : hwang@accumulo test> scan
2018-12-10 11:14:21,107 [shell.Shell] ERROR: java.lang.RuntimeException: org.apache.accumulo.core.client.AccumuloSecurityException: Error PERMISSION_DENIED for user hwang on table test(ID:k) - User does not have permission to perform this action
```
1. 可以看到用户目前没有授权，无法完成操作，并且用户默认是空授权，那么我们上面插入的是三条记录，都是有需要授权的，所以这里结果是空。
2. 接下来我们给用户授权
#### 给用户授权
```bash
hwang@accumulo test> setauths -s A
2018-12-10 11:15:45,514 [Shell.audit] INFO : hwang@accumulo test> setauths -s A
2018-12-10 11:15:45,526 [shell.Shell] ERROR: org.apache.accumulo.core.client.AccumuloSecurityException: Error PERMISSION_DENIED for user hwang - User does not have permission to perform this action
```
1. 可以看到用户默认情况下是没有权限的，必须要有System.ALTER_USER这个权限才可以，root用户默认是有的。
```bash

hwang@accumulo test> user root
2018-12-10 11:25:39,781 [Shell.audit] INFO : hwang@accumulo test> user root
Enter password for user root: *****
root@accumulo test> grant -s Table.READ -u hwang
2018-12-10 11:25:48,474 [Shell.audit] INFO : root@accumulo test> grant -s Table.READ -u hwang
root@accumulo test> setauths -s A -u hwang
2018-12-10 11:26:02,642 [Shell.audit] INFO : root@accumulo test> setauths -s A -u hwang
```
2. 上面我们给hwang用户授权了A，我们在读取下数据
```bash
hwang@accumulo test> scan -s A
2018-12-10 11:27:28,398 [Shell.audit] INFO : hwang@accumulo test> scan -s A
row f1:q1 [A]    v1
```
可以看到，我们将row f1 q1 v1这条数据读了出来
3. 
```bash
hwang@accumulo test> user root
2018-12-10 11:28:28,303 [Shell.audit] INFO : hwang@accumulo test> user root
Enter password for user root: *****
root@accumulo test> setauths -s A,B,broccoli -u hwang
2018-12-10 11:28:38,557 [Shell.audit] INFO : root@accumulo test> setauths -s A,B,broccoli -u hwang
hwang@accumulo test> scan

2018-12-10 11:29:05,045 [Shell.audit] INFO : hwang@accumulo test> scan
row f1:q1 [A]    v1
row f2:q2 [A&B]    v2
row f3:q3 [(apple&carrot)|broccoli|spinach]    v3
```
4. 上面我们都是在针对读取数据的授权，Accumulo也允许进行写数据的授权设置，比如我们这里设置为
必须要是这些表已经有的可视权限。
```bash
2018-12-10 11:31:45,569 [Shell.audit] INFO : hwang@accumulo test> user root
Enter password for user root: *****
root@accumulo test> config -t test -s table.constraint.1=org.apache.accumulo.core.security.VisibilityConstraint
2018-12-10 11:31:58,880 [Shell.audit] INFO : root@accumulo test> config -t test -s table.constraint.1=org.apache.accumulo.core.security.VisibilityConstraint
root@accumulo test> user hwang
2018-12-10 11:32:09,072 [Shell.audit] INFO : root@accumulo test> user hwang
Enter password for user hwang: ********
hwang@accumulo test> insert row f4 q4 v4 -l spinach
2018-12-10 11:32:24,344 [Shell.audit] INFO : hwang@accumulo test> insert row f4 q4 v4 -l spinach
    Constraint Failures:
        ConstraintViolationSummary(constrainClass:org.apache.accumulo.core.security.VisibilityConstraint, violationCode:2, violationDescription:User does not have authorization on column visibility, numberOfViolatingMutations:1)
hwang@accumulo test> insert row f4 q4 v4 -l spinach|broccoli
2018-12-10 11:33:00,623 [Shell.audit] INFO : hwang@accumulo test> insert row f4 q4 v4 -l spinach|broccoli
hwang@accumulo test> scan
2018-12-10 11:33:01,903 [Shell.audit] INFO : hwang@accumulo test> scan
row f1:q1 [A]    v1
row f2:q2 [A&B]    v2
row f3:q3 [(apple&carrot)|broccoli|spinach]    v3
row f4:q4 [spinach|broccoli]    v4
```