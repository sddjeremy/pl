import os
import sys

# Returns a list of the names of all immediate subdirectories of a directory.
def subDirs(path):
    return [x for x in os.listdir(path) if x not in [".",".."] and os.path.isdir(path + "/" + x)]

# Returns a list of the names of all .java files in a directory.
def javaFiles(path):
    return [x for x in os.listdir(path) if len(x) >= 5 and x[-5:] == ".java" and not os.path.isdir(path + "/" + x)]

# Returns whether string a starts with string b
def startsWith(a, b):
    if b == "":
        return True
    if a == "":
        return False
    return a[0] == b[0] and startsWith(a[1:], b[1:])


# Returns (# public, # private, # try, # catch)
def examineJavaFile(path):
    f = open(path)
    contents = f.read()
    f.close()

    # FSM-like machine
    # 0  -> nothing found, not in a comment
    # 1  -> a /
    # 2  -> // (in a single-line comment)
    # 3  -> /*
    # 4  -> /*...*
    countPublic = 0
    countPrivate = 0
    countTry = 0
    countCatch = 0
    state = 0
    rules = {   0: {'/':1, 'else':0},
                1: {'/':2, '*':3, 'else':0},
                2: {'\n':0, 'else':2},
                3: {'*':4, 'else':3},
                4: {'*':4, '/':0, 'else': 3}
            }

    while contents != "":
        c = contents[0]
        if c in rules[state]:
            newState = rules[state][c]
        else:
            newState = rules[state]['else']
        if state in [0, 1]:
            if startsWith(contents, "public"):
                countPublic += 1
            elif startsWith(contents, "private"):
                countPrivate += 1
            elif startsWith(contents, "try"):
                countTry += 1
            elif startsWith(contents, "catch"):
                countCatch += 1
        state = newState
        contents = contents[1:]

    return (countPublic, countPrivate, countTry, countCatch)


def examineFolder(path):
    numBytes = 0
    numPublic = 0
    numPrivate = 0
    numTry = 0
    numCatch = 0
    
    javaFilez = javaFiles(path)
    
    for name in javaFilez:
        x = examineJavaFile(path + '/' + name)
        numBytes += x[0]
        numPublic += x[1]
        numPrivate += x[2]
        numTry += x[3]
        numCatch += x[4]
        
    for sub in subDirs(path):
        y = examineFolder(path + '/' + sub)
        numBytes += x[0]
        numPublic += x[1]
        numPrivate += x[2]
        numTry += x[3]
        numCatch += x[4]
        
        
    return numBytes, numPublic, numPrivate, numTry, numCatch
    
    

#print(examineJavaFile(sys.argv[1]))


