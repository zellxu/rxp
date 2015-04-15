To run the application do the following:

First: javac all included java files to compile them.  No other considerations are necessary. 

1. Run the NetEmu emulaotr
2. Start Server: java FxAServer [X] [I] [P] (Replacing X, I, and P with appropriate address and port numbers)
3. Start Client: java FxAClient [X] [I] [P]
4. Any file on the server needs to contained in the /server folder (ex. /server/afile.txt)
5. Files will be downloaded from the server folder and placed in the /client folder