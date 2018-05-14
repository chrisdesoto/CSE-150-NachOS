package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat
{
    //Variables shared between the threads can go here, since threads share memory;
    public enum Place {OAHU, MOLOKOAI};
    public enum Person {CHILD, ADULT, NONE};
    public static BoatGrader bg;

    //Used to let the children tell begin that transfer is complete
    public static Communicator announcer;

    //Used to for boat control logic
    public static Place boatLoc;
    public static Lock boatLock;
    public static Condition boatCondition;
    public static Person pilot;
    public static Person passenger;


    //Used to allow the inhabitants of various islands to see who is there with them
    public static int childrenOahu, adultsOahu, childrenMolokoai, adultsMolokoi;

    public static void selfTest()
    {
        BoatGrader b = new BoatGrader();

        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

        System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
        begin(1, 2, b);

        System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
        begin(3, 3, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Instantiate global variables here
        announcer = new Communicator();

        boatLoc = Place.OAHU;
        boatLock = new Lock();
        boatCondition = new Condition(boatLock);
        pilot = passenger = Person.NONE;

        childrenOahu = children;
        adultsOahu = adults;

        //Used to determine how many more confirmations this function needs to receive before it knows everyone has been
        //moved to Molokai
        int confirmsLeft = children;

        // Create and run threads for the children
        while(children > 0) {
            Runnable childRunnable = new Runnable() {
                public void run() {
                    ChildItinerary();
                }
            };

            System.out.println("Adding child");
            KThread t = new KThread(childRunnable);
            t.setName("Child " + children--);
            t.fork();
        }

        //Create and run threads for the adults
        while(adults > 0) {
            Runnable adultRunnable = new Runnable() {
                public void run() {
                    AdultItinerary();
                }
            };

            KThread t = new KThread(adultRunnable);
            t.setName("Adult " + adults--);
            t.fork();
        }

        KThread.yield();

        //Wait for everyone to migrate to Molokoai
        while(confirmsLeft > 0) {
            announcer.listen();

            //When the children molokoi threads finish, they speak on announcer. We decrement the count each time since
            //each is confirming no on else is on the island. As soon as we hit zero, that means no more children will
            //go check on the island and that they are sure the transfer is complete.
            confirmsLeft--;
        }
    }

    //Helper function to handle moving to Molokoai
    static void rowToMolokoai() {
        Lib.assertTrue(boatLock.isHeldByCurrentThread());
        boatLoc = Place.MOLOKOAI;

        if(pilot == Person.ADULT) {
            adultsOahu--;
            bg.AdultRowToMolokai();
            adultsMolokoi++;
        } else if(pilot == Person.CHILD) {
            childrenOahu--;
            bg.ChildRowToMolokai();
            childrenMolokoai++;
        }

        if(passenger == Person.ADULT) {
            adultsOahu--;
            bg.AdultRideToMolokai();
            adultsMolokoi++;
        } else if(passenger == Person.CHILD) {
            childrenOahu--;
            bg.ChildRideToMolokai();
            childrenMolokoai++;
        }

        pilot = Person.NONE;
        passenger = Person.NONE;
    }


    //Helper function to handle moving to Oahu
    static void rowToOahu() {
        Lib.assertTrue(boatLock.isHeldByCurrentThread());
        boatLoc = Place.OAHU;

        if(pilot == Person.ADULT) {
            adultsMolokoi--;
            bg.AdultRowToOahu();
            adultsOahu++;
        } else if(pilot == Person.CHILD) {
            childrenMolokoai--;
            bg.ChildRowToOahu();
            childrenOahu++;
        }

        if(passenger == Person.ADULT) {
            adultsMolokoi--;
            bg.AdultRideToOahu();
            adultsOahu++;
        } else if(passenger == Person.CHILD) {
            childrenMolokoai--;
            bg.ChildRideToOahu();
            childrenOahu++;
        }

        pilot = Person.NONE;
        passenger = Person.NONE;
    }

    //Thought process the individual adult follows
    static void AdultItinerary()
    {
        //Thread specific variables can go here, since variables stored on the thread stack
        //aren't shared between threads
        Place location = Place.OAHU;

        boatLock.acquire();
        while(location != Place.MOLOKOAI) {
            //Anyone that has the boatLock is safe to access and modify shared variables
            //If the PC is in this loop, the adult is on Oahu, so it can access variables specific to Oahu
            if(boatLoc == location && childrenOahu <= 1 && pilot == Person.NONE) {
                pilot = Person.ADULT;
                rowToMolokoai();
                location = Place.MOLOKOAI;
                break;
            }
            boatCondition.wake();
            boatCondition.sleep();
        }
        boatLock.release();

        KThread.finish();
    }

    //Throught process the individual child follows
    static void ChildItinerary()
    {
        //Thread specific variables can go here, since variables stored on the thread stack
        //aren't shared between threads
        Place location = Place.OAHU;

        boatLock.acquire();
        while(true) {
            //Anyone that has the boatLock is safe to access and modify shared variables
            //Checks to see if the boat is on our island
            if(boatLoc == location) {
                //If we are on molokoai, then we need to bring the boat to Oahu, even if only to bring it for an adult to
                //use to row to Molokoai
                if(location == Place.MOLOKOAI && pilot == Person.NONE) {
                    pilot = Person.CHILD;
                    rowToOahu();
                    location = Place.OAHU;
                    if(childrenOahu == 1 && adultsOahu == 0) {
                        //In this case, everyone has been moved to Molokoai, so we want this thread to execute again so
                        //the child can signal that everyone has been moved once it goes back to Molokoai
                        continue;
                    }
                } else if(location == Place.OAHU) {
                    boolean complete = childrenOahu == 1 && adultsOahu == 0;

                    if(pilot == Person.NONE) {
                        if(childrenOahu > 1) {
                            //Two children should go to Molokoai
                            pilot = Person.CHILD;
                            boatCondition.wake();
                            boatCondition.sleep();
                        } else if(childrenOahu == 1 && adultsOahu == 0) {
                            //This child should row itself back to oahu
                            pilot = Person.CHILD;
                            rowToMolokoai();
                        }
                    } else if(pilot == Person.CHILD) {
                        //If the pilot is a child, then lets join them as a passenger and together row to Molokoai
                        passenger = Person.CHILD;
                        rowToMolokoai();
                    }
                    location = Place.MOLOKOAI;

                    if(complete) {
                        //If there was only one child on Oahu and zero adults, then we can signal to begin we are done
                        boatCondition.wake();
                        break;
                    }
                }
            }
            boatCondition.wake();
            boatCondition.sleep();
        }
        boatLock.release();
        //Signal to Oahu that we confirm everyone has travelled to Molokoai
        announcer.speak(1);

        KThread.finish();
    }
}
