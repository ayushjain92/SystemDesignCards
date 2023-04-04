# System Design Cards

---------
## Concurrency

1. [ConcurrentHashMap](https://itsromiljain.medium.com/curious-case-of-concurrenthashmap-90249632d335)
2. Distributed locks
2. Versioned writes in db

If lock causes issues then partition. When reddit was getting a lot of upvotes for their trending posts then they partitioned their upvote queries. 


---------

## Hot Partitions
How to handle? For e.g., our hash is on `videoId` then how do we handle hot partitions to calculate view counts for a video? Happens in cases of viral videos!

#### Solution:
1. **Re-Hash**: Include timestamp in the key so that all instances of video will be evenly distributed among the partitions.
2. **Split Partition**: Split a hot partition into multiple partitions using consistent hashing. 2-layer cluster where a single cluster re-directs to another cluster.
1. `[BONUS]` **Dedicated Partition**: Create a dedicated partition to handle workloads from popular channels like Mr. Beast, PewDieDie, T-Series, etc.?

--------

## Replication
How to achieve?

#### Solution:
1. **Single-leader replication**: Each partition with a single leader.
2. **Leaderless replication**: Each host talk to each other using gossip protocol.
3. **Multi-leader replication**: Used for replicating data among different DCs. Each DC will have it's own leader node.
4. **Replication Factor**: how many nodes should have data copies.
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

1. Used in multi-player online gaming algorithms. For example, when we take headshots in Counter Strike then the event is sent with timestamp of the shot and then the backend server matches if the position of player-B was matching with the shot direction for a given timestamp.  


#### Trade-Offs:
1. High availability v/s low consistency
2. Easy to rollback all events after timestamp T.
3. Easy to migrate to new service, new service need to consume events. Gateway services like BGCVGS/ABDMSil need to store timestamps.
4. Transaction Guarantee
   5. Atleast once: invoice email, should send atleast once
   6. Atmost once: welcome email, do not care if we do not send it as well.
5. Queue overfill: Priority Queues for high bandwidth/tps events.
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

### URL in browser


![img1](Untitled Diagram.drawio.svg)