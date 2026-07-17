# Captain's log


## 2026-07-17 - ModuleOp interface for unified inter-module communication

# Commits

- b388013 - Added ModuleOp capability for unified inter-module communication
- 764c747 - Introduce generic ModuleOp dispatch abstraction
- 939157e - Change API naming for clarity and consistency
- 354ca20, 192aec3 - Add/modify module communication API
- c8610ff, 2a7e95b - Change ModuleOp API by introducing specialized operation argument types
- 52e4f56 - Add property get() support to ModuleOp
- 50402a8 - Added ModuleOp commands and execute()


## 2026-07-15 - Remodel RemoteCallMux into a service registry

# Commits

- 62f6bd0 - Remodel RemoteCallMux into a composable service registry


## 2026-07-06 - Refactor PipeMux subscriptions and shared communication domains

# Commits

- fbac78c - Refactor PipeMux to use unified PipeMessage transport objects
- bec5086, 36d38cc - Refactor PipeMux subscriptions and shared communication domains
- 491dfee - Change messaging interfaces and naming to a generic message bus API
- 3d77c50 - Consolidate and clean up the Pipe Id names


## 2026-06-24 - Add WiFi error cooldown mechanism

# Commits

- bb217dc - Expose latest P2P error information in the debug menu
- 576bdcf - Refactor WiFi Direct P2P action execution into reusable helper
- 7a1cbd5 - Unify WiFi Direct error type/handling
- 3634ae4 - Add WiFi error cooldown mechanism with tracker-based suppression
- dace188 - Refactor BUSY error handling: unify retry/error logic and add capabiilty to filter retries for meaningful operations
- 4bd0f4d - Add cancelConnect operation after a failed connect / group forming operation


## 2026-06-18 - Transition to an event-driven state machine architecture for the WIFIDirect module

# Commits

- 33efc64, 03d8604, d2f940b, f8ce979, 2896dd6, 173fad4, 5b984d4, 5c2b609, fe80ba0, 021c344, 6456cce
- f2c8e66, 6141708, 6b94095, 00498bc, 618868c, d50949f, 6a72595, 4d6002a, 111e575, f8ce979, 2896dd6
- 173fad4, 173fad4, 5b984d4, 5c2b609, fe80ba0, 021c344, 6456cce, f2c8e66, 6141708, 6b94095, 00498bc
- 618868c, d50949f, 6a72595, 4d6002a, 111e575, c327da1, 5ddd4ce, d7ce00a, d815cb6, ea4c5de, 2ee54b1
- 8558c76, 32460cf, d8290fd, 3c408ca, 33efc64, 03d8604, f8ce979


## 2026-06-02 - Various infrastucture refactors

# Commits

- 4692c84 - Add suspend-based FIFO Mailbox using Channel with bounded capacity, timeout receive
- 1326b7b - Remove exception handling in WiFi P2P state results
- 30100a4 - Explicit exception handling in WiFi P2P state results
- a2f19d3 - UI Screens cleanup and auto-update on changes
- afe07f4, bbf2d5c - Protect message handling with mutex synchronization


## 2026-05-29 - WifiDirect module refactor: split platform and logic layers (WIP)

# Commits

- a723c16 - Refactor ChannelMux synchronization and registration
- 8bc9993, 698fa6b - Refactor WTWifiDirectManager
- db7b826, 9979679 - Split platform and logic layers
- d87ccc2, 456aebf - Refactor WifiDirect APIs to unified result model (Success/Data/Error)
- 032b4fd - Split foundation and logic layers
- cf10131 - Incremental refactoring and cleanup for app scope transition
- 304d6b0 - Refactor WiFi P2P module initialization stage
- 56e074e - Incremental refactoring and cleanup for app scope transition


## 2026-05-21 - Unify coroutine runtime scope across application modules

# Commits
- 9782745


## 2026-05-20 - Refactor blocking queue implementation to use Kotlin Channels for buffering and backpressure handling

# Commits
- e6cc9ba


## 2026-05-20 - Add Gate abstraction for coroutine wait/timeout with external open signal

# Commits
- a557ce0


## 2026-05-18 - Add coroutine await helpers for callback-based APIs

# Commits
- f6a9467


## 2026-05-15 - Add wrapper for structured coroutine execution and runtime dispatch selection

# Commits
- 28ddd70


## 2026-05-11 - Refactor Wi-Fi P2P async callbacks to coroutine suspension model

# Commits
- ca9b5f0, d18bf28, 4cac504


## 2026-05-07 - Add CHANGELOG.md file 


## 2026-05-05 - Refactor RemoteCallMux dispatcher for safer typing

### Commits
- 4478605


## 2026-05-05 - Improve interface separation and naming consistency

### Commits
- 61f375d, a564fc8 - Update modules build.gradle files
- 220c72f - Rename 'glue' modules to 'app-api' and 'util-api' for clearer interface separation and naming consistency


## 2026-04-30 - Clean up Android manifests (permissions, attributes)

### Commits
- 1425f28


## 2026-04-29 - Unify Gradle version catalog definitions

### Commits
- 33ee453


## 2026-04-28 - Update toolchain versions

### Commits
- 275b7c7, 1ba6556, 86450d0, c2eafd4


## 2026-04-26 - Increment version for Google Play

### Commits
- 44a2bce


## 2026-04-22 - Publish to Github

### Commits
- 44ac7b1, 8424d4e


## 2026-04-17 - Add README.md and LICENSE files
