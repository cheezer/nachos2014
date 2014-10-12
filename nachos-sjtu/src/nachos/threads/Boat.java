package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
	static BoatGrader bg;
	private static int childOnANum, adultOnANum, childOnBNum;
	private static int cOnBoatANum;
	private static Lock islandA = new Lock(), islandB = new Lock();
	private static Condition2 adultA = new Condition2(islandA);
	private static Condition2 childA = new Condition2(islandA), childB = new Condition2(islandB);
	private static Condition2 cOnBoatA = new Condition2(islandA);
	private static Semaphore done = new Semaphore(0);
	private static boolean boatA;
	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		childOnANum = children;
		childOnBNum = 0;
		adultOnANum = adults;
		cOnBoatANum = 0;
		boatA = true;
		Lib.debug('b', "boat begin");
		for (int i = 0; i < adults; i++)
			new KThread(new Runnable() {
						public void run() {
							AdultItinerary();
						}
					}
			).setName("Adult " + i).fork();
		for (int i = 0; i < children; i++)
			new KThread(new Runnable(){
				public void run()
				{
					ChildItinerary();
				}
			}
			).setName("Child " + i).fork();
		done.P();
	}

	static void AdultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */
		islandA.acquire();
		while (!(childOnANum <= 1 && boatA))
			adultA.sleep();
		bg.AdultRowToMolokai();
		Lib.debug('b', "one adult to B");
		boatA = false;
		adultOnANum--;
		islandA.release();
		islandB.acquire();
		childB.wake();
		islandB.release();
	}

	static void ChildItinerary() {
		while (childOnANum + adultOnANum > 1)
		{
			islandA.acquire();
			if (adultOnANum > 0)
				adultA.wake();
			while (!(cOnBoatANum < 2 && boatA))
				childA.sleep();
			if (cOnBoatANum == 0)
			{
				cOnBoatANum++;
				childA.wake();
				cOnBoatA.sleep();
				bg.ChildRideToMolokai();
				Lib.debug('b', "two children to B");
				cOnBoatA.wake();
			}
			else if (cOnBoatANum == 1)
			{
				cOnBoatANum++;
				bg.ChildRowToMolokai();
				cOnBoatA.wake();
				cOnBoatA.sleep();
			}
			cOnBoatANum--;
			childOnANum--;
			boatA = false;
			islandA.release();
			islandB.acquire();
			childOnBNum++;
			if (cOnBoatANum == 1)
				childB.sleep();
			//sailing to A
			childOnBNum--;
			islandB.release();
			bg.ChildRowToOahu();
			Lib.debug('b', "one children to A");
			islandA.acquire();
			childOnANum++;
			boatA = true;
			islandA.release();
		}
		Lib.debug('b', "one children to B");
		islandA.acquire();
		childOnANum--;
		islandA.release();
		bg.ChildRowToMolokai();
		islandB.acquire();
		childOnBNum++;
		islandB.release();
		done.V();
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
