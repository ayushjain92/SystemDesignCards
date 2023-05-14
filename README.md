# System Design Cards

---------
## Concurrency 

1. [ConcurrentHashMap](https://itsromiljain.medium.com/curious-case-of-concurrenthashmap-90249632d335)
2. Distributed Locks
2. Versioned Writes in db

If lock causes issues then partition. When reddit was getting a lot of upvotes for their trending posts then they partitioned their upvote queries. 

### Concurrency based on cores

| Core        | How Concurrency Works? | Examples                                                                                                                                                                                         | 
|-------------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Single Core | Context Switching      | ![Context Switch](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*_HglrgsHLrFrSxaFfGw8fA.png) ![](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*E3lhTuU_P3bePvL6Nfwf_A.jpeg) |
| Multi-Core  | Parallelism            | ![Parallelism](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*QbyO_eNcYHw8cUpvVR5AZw.jpeg)                                                                                             |                                                                                       

### LLD

#### ConcurrentHashMap
![ConcurrentHashMap](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*U3oE8gg95rTulEJQGG10GQ.png)
```java

/**
new ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel);
 Initial capacity of map is 100 which means ConcurrentHashMap will make sure it has space for adding 100 key-value pairs after creation.

 LoadFactor is 0.75f which means when average number of elements per map exceeds 75 (initial capacity * load factor = 100 * 0.75 = 75) at that time map size will be increased and existing items in map are rehashed to put in new larger size map.

 Concurrency level is 10, it means at any given point of time Segment array size will be 10 or greater than 10, so that 10 threads can able to write to a map in parallel.
 **/
ConcurrentHashMap map = new ConcurrentHashMap(100, 0.75f, 10);
```


#### CompletableFuture
```java

CompletableFuture.supplyAsync(() -> getUser(userId))
        .thenApply(CreditRatingService::getCreditRatingSystem1)
        .thenAccept(System.out::println);

CompletableFuture.supplyAsync(() -> getUser(userId))
        .thenApplyAsync(CreditRatingService::getCreditRatingSystem1)
        .thenAcceptAsync(System.out::println);
```

1. The difference is in the `async` suffix on the method names. The methods without async execute their task in the same thread as the previous task. So in the first example, all getUser, getCreditRating and println are executed in the same thread. It’s OK, it’s still a thread from the fork-join pool, so the main thread is not blocked.
2. The second variant always submits the succeeding task to the pool, so each of the tasks can be handled by different thread. The result will be the same, the first variant is a bit more effective due to less thread switching overhead. It does not make any sense to use the async variant here

`applyAsync` variant is used for the below use-cases:

```java
CompletableFuture<User> user = CompletableFuture.supplyAsync(() -> getUser(userId));

CompletableFuture<CreditRating> rating1 = 
    user.thenApplyAsync(CreditRatingService::getCreditRatingSystem1);
CompletableFuture<CreditRating> rating2 = 
    user.thenApplyAsync(CreditRatingService::getCreditRatingSystem2);

rating1
    .thenCombineAsync(rating2, CreditRating::combine)
    .thenAccept(System.out::println);
```

1. Since we want to do it in parallel, we have to use at least one async method. Without async, the code would use only one thread so both credit rating tasks would be executed serially.
2. We have added combine phase that waits for both credit rating tasks to complete. It’s better to make this async too, but from different reason. Without async, the same thread as in rating1 would be used. But we do not want to block the thread while waiting for the rating2 task. You want to return it to the pool and get a thread only when it is needed.
3. When both tasks are ready, we can combine the result and print it in the same thread, so the last thenAccept is without async.

```java
CompletableFuture.supplyAsync(() -> {
    try {
        // divide by 0;
    } catch (Exception e) {
        throw new RuntimeException("err", e);
    }}).thenAcceptAsync().exceptionally(e -> {
            System.err.println("Error! " + e.getMessage());
            return null;
});
```

##### CompletableFuture v/s ExecutorService
*ExecutorService:*
```java
public ReturnSomething parent(){
    child();
    ...//rest to UI
}

private void child() {
  ExecutorService executorService = Executors.newFixedThreadPool(3);
  
  executorService.submit( () -> { 
      MyFileService.service1();
  });
  executorService.submit(() -> {
        MyFileService.service2();
  });
  executorService.submit(() -> {
        MyFileService.service3();
  });
}
```
*CompletableFuture:*
```java
public ReturnSomething parent() {
    child();
    ...//rest to UI
}

private void child() {
    CompletableFuture.supplyAsync(() ->  MyFileService.service1();
    CompletableFuture.supplyAsync(() ->  MyFileService.service2();
    CompletableFuture.supplyAsync(() ->  MyFileService.service3();
}
```

Functionally, the above 2 approaches are more or less the same:you submit your tasks for execution; you don't wait for the result.

Technically, however, there are some subtle differences:
1. In the second approach, you didn't specify an executor, so it will use the **common ForkJoinPool**. You would have to pass an executor as second argument of supplyAsync() if you don't want that;
1. The CompletableFuture API allows to easily chain more calls with thenApply(), thenCompose() etc. It is thus more flexible than the simple Future returned by ExecutorService.submit();
1. Using CompletableFuture allows to easily return a future from your child() method using return CompletableFuture.allOf(the previously created futures).

###### Better Hybrid Approach:
```java
CompletableFuture.supplyAsync(MyFileService::service1, executorService);
CompletableFuture.supplyAsync(MyFileService::service2, executorService);
CompletableFuture.supplyAsync(MyFileService::service3, executorService);
```
We should never use common ForkJoinPool for blocking I/O calls like calling a micro-service/database calls etc. In general, there are two types of tasks: computational and blocking. If you have more than three available CPUs, then your commonPool is automatically sized to two threads and you can very easily block execution of any other part of your system that uses the commonPool at the same time by keeping the threads in a blocked state.

### References
[1](https://blog.krecan.net/2013/12/25/completablefutures-why-to-use-async-methods/), [2](https://codeflex.co/java-multithreading-completablefuture-explained/), [3](https://stackoverflow.com/questions/27723546/completablefuture-supplyasync-and-thenapply), [4](https://stackoverflow.com/questions/52303472/executorservice-vs-completablefuture)

---------

## Hot Partitions
How to handle? For e.g., our hash is on `videoId` then how do we handle hot partitions to calculate view counts for a video? Happens in cases of viral videos!

#### Solution:
1. **Re-Hash**: Include timestamp in the key so that all instances of video will be evenly distributed among the partitions.
2. **Split Partition**: Split a hot partition into multiple partitions using consistent hashing. 2-layer cluster where a single *Proxy cluster* re-directs to actual cluster.
1. `[BONUS]` **Dedicated Partition**: Create a dedicated partition to handle workloads from popular channels like Mr. Beast, PewDieDie, T-Series, etc.?

--------

## Replication
How to achieve?

#### Solution:
1. **Single-leader replication**: Each partition with a single leader.
2. **Leaderless replication**: Each host talk to each other using gossip protocol.
3. **Multi-leader replication**: Used for replicating data among different DCs. Each DC will have it's own leader node.
1. `[BONUS]` **Redundancy**: Fast Reads. In the consistent hashing ring, every node should have a copy of data from it's previous node. This helps in achieving low latency when a node fails and the node in front of it start serving its request.


#### Distributed Consensus in cases of node failures:
1. Quoram
2. 

#### Trade-Offs:
1. Achieves Availability but latency suffers. 

------------


## Client Side Protocol
* netty


------------

## Testing Scalability

1. **Load Testing**: increase tps
2. **Stress Testing**: breaking point in the system.
3. **Soak Testing**: Generate high load so that memory leaks can be identified.
4. **Netflix Chaos Monkey**: Brings down any random host from host pool at a given frequency so that any single point of failures can be identified.


------------

## Monitoring

1. Latency
2. Error
3. Traffic
4. Saturation 

-------

## How to handle Thundering Herds/Cascading Failures?

*Cascading Failures*: when one server fails, other servers start failing too resulting in a domino effect of failing each server. 

1. **Rate Limiting**: Assign each server a request queue with a pre-determined capacity. Queue Capacity can be determined based on server’s compute power, cpu, etc. 
2. **Leasing Cache Requests**: Lease get calls to Memcache returns following responses:
    1. `FILL`: Request will get the latest value from db and fill the cache. 
    2. `WAIT/USE_STALE`: Any concurrent request read old value from cache or wait for the new value to be filled. 
    3. Lesser load on the primary database. 
    4. Instagram/Facebook uses heavily to display mostly stale likes/view counts/etc. 
3. **Viral** :
    1. Rate limiting + Auto scaling 
4. **Black Friday**: 
    1. Do pre-scale based on load factor. 
    2. Also attach auto scaling. 
5. **Job Scheduling**:
    1. Always In batches if possible + sleep in between request calls. 
6. **Popular Post**:
    1. Twitter do not pre-populate celebrity posts in user’s feed cache. Get calls for Posts from celebrities I follow are made at runtime when I open my app and those cards are merged in my cache at some pre-calculated positions.
    2. If you must send notifications, then add jitter, YouTube uses jitter to send notifications to followers of bell icon for popular pages
7. **Approximation**: Approximations are used for displaying like/view counts since those are metadata and not core feature of the product. 
8. If scale is very high, invest in hardware. 


-----

## Consistency 

Two generals problem

1. Leader-Follower roles. 
2. 2-Phase Commit. 
1. Quoram
3. DynamoDB write requests writes to the leader node synchronously and start a worker pool to write to the follower nodes(async wait). As soon as any one follower node writes the data successfully, client is returned successful response.



------------

## Event Driven Architecture

1. Used in multi-player online gaming algorithms. For example, when we take headshots in Counter Strike then the event is sent with timestamp of the shot and then the backend server matches if the position of player-B was matching with the shot direction/location for a given timestamp.  


#### Trade-Offs:
1. High availability v/s low consistency
2. Easy to rollback all events after timestamp T.
3. Easy to migrate to new service, new service need to consume events. Gateway services like BGCVGS/ABDMSil need to store timestamps.
4. Transaction Guarantee
   1. Atleast once: invoice email, should send atleast once
   2. Atmost once: welcome email, do not care if we do not send it as well.
5. Queue overfill: Priority Queues for high bandwidth/tps events. P-0 Queue to handle Sev1/Sev2, P-1 Queue to handle workloads from production code, P-2 Queue to handle workloads from Customer Support/Production testing.
6. Difficult to move out of.


------------

## No-Sql v/s MySql

#### Trade-offs
##### Advantages of NoSQL
1. When `select *` is always needed.
2. Insertions require the whole blob. for e.g., 
3. Schema is flexible. adding new column is really easy. 
4. Horizontal partitions are inbuilt. more focused on availability (check replication).
5. Built for aggregations/metrics/analysis.

##### Disadvantages
1. Not built for updates.
7. ACID is not guaranteed. Transactions not guaranteed.
8. Not read optimized on a column. For e.g., difficult to get all employees with age>30.
9. Relations are not implicit.
10. Joins are expensive.

#### How No-SQL data is stored?
1. In log files. 
2. Data is kept in self sorted structures like (AVL/ Red-Black Trees) in memory, and once the memory is past some threshold value (say ~50kb), then the entire memtable(the self sorted trees) are dumped into a SSTable (on disk) which is efficient as the data is already sorted.

-----------------


### OSI Model

1. **Application Layer:** Protocols (FTP/HTTP(S)/SMTP/Telnet) used for n/w applications like Chrome,Email,Skype,etc.
1. **Presentation Layer:** Received data from application layer. Converts texts to binary format. Protocols for : Data Compression (video/images) + Conversion + Encryption Protocols(SSL).  
1. **Session Layer:** APIs to communicate to each other. Session Management + Authentication (username/password) + Authorization. 
1. **Transport Layer:** Reliability of communication. divides data into List<Segment>. 
   ```
      class TransportLayer {
         List<String> segmentIds;
         double dataTransmissionRate; // This tells server at what rate a client can accept the data from server like 10MBps (based on mobile n/w configurartions). Server makes a decision on data packets to be transmitted after looking at this value. There should be a retry + jitter mechnism here to increase this value so that max bandwidth can be defined be the client application?
      }
   
      class Segment {
         String segmentId;
         int seqNo; // packet sequence number
         Port port; // used to direct each data to correct server application.
         String data; // 
      }
   
      class Port {
         int sourcePort;
         int destinationPort;
      }
   ```
   Data corruption techniques are also used here. Retries are used for the data packets not matching checksums at this layer if we use TCP.
   1. Involved in Segmentation + Flow Control + Error Control 
1. **Network Layer**: Receives from Transport Layer. Routers reside here. Logical Addressing (IP Address) + Routing + Path Determination.
   ```
      class NetworkLayer {
         String sourceIP;
         String destinationIP;
         String segmentId; 
      }
   ```
   Path Determination: OSPF(Open shortest Path First), BGP(Border Gateway Protocol), IS-IS 
1. **Data Layer**: Mac Addresses are assigned to sender/receiver of each data packet. Mac Address is Embedded in NIC Card by manufacturer.
1. **Physical Layer**: Converts binary frames to signals.


---------


### Why do we need a persistent TCP connection in cases of live streams/chat applications etc.?

To send traffic to clients, the server and client must establish a persistent TCP connection. The need for a persistent connection is because the stream must continue to come in, and creating new connections each time would take more time and make the video not remain live. To maintain a persistent connection, there are various technologies available, such as **Web Sockets**.