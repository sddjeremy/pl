import os
import sys

# Returns a list of the names of all immediate subdirectories of a directory.
def subDirs(path):
    return sorted([x for x in os.listdir(path) if x not in [".",".."] and os.path.isdir(path + "/" + x)])

# Returns a list of the names of all .java files in a directory.
def javaFiles(path):
    return sorted([x for x in os.listdir(path) if len(x) >= 5 and x[-5:] == ".java" and not os.path.isdir(path + "/" + x)])

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
    # Quotes outside comments...
    # 5  -> "
    # 6  -> "...\
    # 7  -> '
    # 8  -> '...\
    # Quotes inside single-line comments...
    # 9  -> //..."
    # 10 -> //..."...\
    # 11 -> //...'
    # 12 -> //...'...\
    # Quotes inside multi-line comments...
    # 13 -> /*..."
    # 14 -> /*..."...\
    # 15 -> /*...'
    # 16 -> /*...'...\
    countBytes = os.stat(path).st_size
    countPublic = 0
    countPrivate = 0
    countTry = 0
    countCatch = 0
    state = 0
    rules = {   0: {'/':1, '"':5, "'":7, 'else':0},
                1: {'/':2, '*':3, 'else':0},
                2: {'\n':0, 'else':2},
                3: {'*':4, 'else':3},
                4: {'*':4, '/':0, 'else':3},

                5: {'"':0, '\\':6, 'else':5},
                6: {'else':5},
                7: {"'":0, '\\':8, 'else':7},
                8: {'else':7},

                9: {'"':2, '\\':10, 'else':9},
                10: {'else':9},
                11: {"'":2, '\\':12, 'else':11},
                12: {'else':11},

                13: {'"':3, '\\':14, 'else':13},
                14: {'else':13},
                15: {"'":3, '\\':16, 'else':15},
                16: {'else':15}
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

    return (countBytes, countPublic, countPrivate, countTry, countCatch)

# Does recursive counts of bytes, publics, privates, tries, catches
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
        x = examineFolder(path + '/' + sub)
        numBytes += x[0]
        numPublic += x[1]
        numPrivate += x[2]
        numTry += x[3]
        numCatch += x[4]
        
        
    return numBytes, numPublic, numPrivate, numTry, numCatch
    
# Recursively prints the information for a folder
def printOutput(path):
    x = examineFolder(path)
    temp = path
    if len(temp) < 31:
        temp += (31 - len(temp)) * " "
    print(temp)
    print("%8i bytes\t%3i public\t%3i private\t%3i try\t%3i catch" % x)
    for sub in subDirs(path):
        printOutput(path + '/' + sub)
        

printOutput(sys.argv[1])
